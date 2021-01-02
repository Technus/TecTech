package com.github.technus.tectech.thing.item;

import com.github.technus.tectech.util.CommonValues;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

import static com.github.technus.tectech.Reference.MODID;
import static com.github.technus.tectech.loader.gui.CreativeTabTecTech.creativeTabTecTech;

public class PowerPassUpgradeItem {

    public static final Item POWER_PASS_ENABLER = new Item() {

        @Override
        @SuppressWarnings("unchecked")
        public void addInformation(ItemStack aStack, EntityPlayer ep, List aList, boolean boo) {
            aList.add(CommonValues.TEC_MARK_GENERAL);
            aList.add("Used to enable Power Pass on certain Multis");
        }

    }       .setTextureName(MODID + ":itemPowerPassEnabler")
            .setUnlocalizedName("PowerPassEnabler")
            .setCreativeTab(creativeTabTecTech);

    static {
        GameRegistry.registerItem(POWER_PASS_ENABLER, "PowerPassEnabler");
    }
}
