package com.github.technus.tectech.thing.metaTileEntity.multi.base;

import com.github.technus.tectech.thing.item.PowerPassUpgradeItem;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GT_Utility;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public abstract class GT_MetaTileEntity_PowerPassUpgradeable_EM  extends GT_MetaTileEntity_MultiblockBase_EM implements IPowerPassUpgradeable{

    private boolean isPowerPassUpgraded = false;

    @Override
    public boolean isPowerPassUpgraded() {
        return isPowerPassUpgraded;
    }

    @Override
    public void setPowerPassUpgraded(boolean powerPassUpgraded) {
        isPowerPassUpgraded = powerPassUpgraded;
    }

    protected GT_MetaTileEntity_PowerPassUpgradeable_EM(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    protected GT_MetaTileEntity_PowerPassUpgradeable_EM(String aName) {
        super(aName);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aBaseMetaTileEntity.isClientSide())
            return true;
        ItemStack aStack = aPlayer.getCurrentEquippedItem();
        if ((!this.isPowerPassUpgraded()) && GT_Utility.consumeItems(aPlayer, aStack, PowerPassUpgradeItem.POWER_PASS_ENABLER, 1))
            this.setPowerPassUpgraded(true);
        else
            aBaseMetaTileEntity.openGUI(aPlayer);
        return true;
    }

    @Override
    public void setItemNBT(NBTTagCompound aNBT) {
        if (!this.isPowerPassUpgraded())
            return;
        aNBT.setBoolean("ePassUpgrade", true);
        if (this.ePowerPass)
            aNBT.setBoolean("ePass", true);
        super.setItemNBT(aNBT);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        if (this.isPowerPassUpgraded())
            aNBT.setBoolean("ePassUpgrade", true);
        super.saveNBTData(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        this.setPowerPassUpgraded(aNBT.getBoolean("ePassUpgrade"));
        super.loadNBTData(aNBT);
    }
}