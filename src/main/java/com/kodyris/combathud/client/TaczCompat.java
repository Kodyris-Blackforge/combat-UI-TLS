package com.kodyris.combathud.client;

import com.kodyris.combathud.CombatHud;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reads TACZ gun state by reflection, so this mod needs no TACZ jar to compile and still
 * runs (without ammo info) if TACZ is missing or its API changes. The ammo logic mirrors
 * TACZ's own GunHudOverlay (tacz 1.20.1-1.1.8-hotfix2). Nothing here draws or removes TACZ's HUD.
 */
final class TaczCompat {
    private TaczCompat() {}

    /**
     * mag: rounds in the magazine (for inventory-fed guns: rounds available in the inventory).
     * reserve: -1 infinite, otherwise ammo carried beyond the magazine (0 for inventory-fed guns).
     * chambered: a round in the barrel that TACZ counts as +1 (never true for open-bolt guns).
     * invAmmo: the gun fires straight from inventory ammo, so there is no separate reserve.
     */
    record GunInfo(int mag, int maxMag, int reserve, String mode, boolean chambered, boolean invAmmo) {}

    private static boolean initDone, ok;
    private static Method getIGun, getGunId, getMag, hasChamber, getFireMode, useDummy, useInvAmmo, getDummy;
    private static Method fromLiving, needCheck;
    private static Class<?> ammoCls, boxCls;
    private static Method isAmmoOfGun, boxOfGun, boxCreative, boxAllCreative, boxCount;
    private static Method getClientIndex, getGunData, getBolt, maxAmmo;

    private static void init() {
        initDone = true;
        try {
            Class<?> igun = Class.forName("com.tacz.guns.api.item.IGun");
            getIGun     = igun.getMethod("getIGunOrNull", ItemStack.class);
            getGunId    = igun.getMethod("getGunId", ItemStack.class);
            getMag      = igun.getMethod("getCurrentAmmoCount", ItemStack.class);
            hasChamber  = igun.getMethod("hasBulletInBarrel", ItemStack.class);
            getFireMode = igun.getMethod("getFireMode", ItemStack.class);
            useDummy    = igun.getMethod("useDummyAmmo", ItemStack.class);
            useInvAmmo  = igun.getMethod("useInventoryAmmo", ItemStack.class);
            getDummy    = igun.getMethod("getDummyAmmoAmount", ItemStack.class);

            Class<?> operator = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            fromLiving = operator.getMethod("fromLivingEntity", LivingEntity.class);
            needCheck  = operator.getMethod("needCheckAmmo");

            ammoCls = Class.forName("com.tacz.guns.api.item.IAmmo");
            isAmmoOfGun = ammoCls.getMethod("isAmmoOfGun", ItemStack.class, ItemStack.class);

            boxCls = Class.forName("com.tacz.guns.api.item.IAmmoBox");
            boxOfGun = boxCls.getMethod("isAmmoBoxOfGun", ItemStack.class, ItemStack.class);
            boxCreative = boxCls.getMethod("isCreative", ItemStack.class);
            boxAllCreative = boxCls.getMethod("isAllTypeCreative", ItemStack.class);
            boxCount = boxCls.getMethod("getAmmoCount", ItemStack.class);

            Class<?> timeless = Class.forName("com.tacz.guns.api.TimelessAPI");
            getClientIndex = timeless.getMethod("getClientGunIndex", ResourceLocation.class);
            getGunData = Class.forName("com.tacz.guns.client.resource.index.ClientGunIndex").getMethod("getGunData");
            Class<?> gunDataCls = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
            getBolt = gunDataCls.getMethod("getBolt");
            maxAmmo = Class.forName("com.tacz.guns.util.AttachmentDataUtils")
                    .getMethod("getAmmoCountWithAttachment", ItemStack.class, gunDataCls);
            ok = true;
        } catch (Throwable t) {
            CombatHud.LOGGER.warn("[CombatHud] TACZ API not found, ammo info disabled: {}", t.toString());
        }
    }

    static GunInfo read(Player p, ItemStack gunStack) {
        if (!initDone) init();
        if (!ok) return null;
        try {
            Object gun = getIGun.invoke(null, gunStack);
            if (gun == null) return null;

            // gun definition: magazine capacity (with attachments) and bolt type
            int maxMag = 0;
            boolean openBolt = false;
            try {
                Optional<?> index = (Optional<?>) getClientIndex.invoke(null, getGunId.invoke(gun, gunStack));
                if (index.isPresent()) {
                    Object data = getGunData.invoke(index.get());
                    maxMag = (int) maxAmmo.invoke(null, gunStack, data);
                    Object bolt = getBolt.invoke(data);
                    openBolt = bolt instanceof Enum<?> e && e.name().equals("OPEN_BOLT");
                }
            } catch (Throwable ignored) {
                // keep the fallbacks
            }

            // TACZ only counts the chambered round as +1 when the bolt is not open
            boolean chambered = (boolean) hasChamber.invoke(gun, gunStack) && !openBolt;
            String mode = String.valueOf(getFireMode.invoke(gun, gunStack)).toUpperCase();

            boolean invAmmo = (boolean) useInvAmmo.invoke(gun, gunStack);
            int mag, reserve;
            if (invAmmo) {
                int inv = inventoryCount(p, gunStack);          // fires straight from inventory ammo
                mag = inv < 0 ? 9999 : Math.min(9999, inv);
                reserve = 0;
            } else {
                mag = (int) getMag.invoke(gun, gunStack);
                reserve = reserve(p, gun, gunStack);
            }
            if (maxMag <= 0) maxMag = mag;
            return new GunInfo(mag, maxMag, reserve, mode, chambered, invAmmo);
        } catch (Throwable t) {
            ok = false;   // stop retrying every frame
            CombatHud.LOGGER.warn("[CombatHud] TACZ ammo read failed, disabling: {}", t.toString());
            return null;
        }
    }

    /** Mirrors GunHudOverlay.handleCacheCount for magazine-fed guns. */
    private static int reserve(Player p, Object gun, ItemStack gunStack) throws Exception {
        if ((boolean) useDummy.invoke(gun, gunStack)) {
            Object operator = fromLiving.invoke(null, p);
            return (boolean) needCheck.invoke(operator) ? (int) getDummy.invoke(gun, gunStack) : -1;
        }
        return inventoryCount(p, gunStack);
    }

    /** Matching ammo in the inventory (loose ammo + ammo boxes); -1 = infinite. Mirrors handleInventoryAmmo. */
    private static int inventoryCount(Player p, ItemStack gunStack) throws Exception {
        Object operator = fromLiving.invoke(null, p);
        if (!(boolean) needCheck.invoke(operator)) return -1;

        int total = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            Item item = s.getItem();
            if (ammoCls.isInstance(item)) {
                if ((boolean) isAmmoOfGun.invoke(item, gunStack, s)) total += s.getCount();
            } else if (boxCls.isInstance(item) && (boolean) boxOfGun.invoke(item, gunStack, s)) {
                if ((boolean) boxAllCreative.invoke(item, s) || (boolean) boxCreative.invoke(item, s)) return -1;
                total += (int) boxCount.invoke(item, s);
            }
        }
        return total;
    }
}
