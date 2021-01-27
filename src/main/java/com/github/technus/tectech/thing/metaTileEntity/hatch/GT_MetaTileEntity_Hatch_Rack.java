package com.github.technus.tectech.thing.metaTileEntity.hatch;

import com.github.technus.tectech.util.CommonValues;
import com.github.technus.tectech.Reference;
import com.github.technus.tectech.TecTech;
import com.github.technus.tectech.util.Util;
import com.github.technus.tectech.thing.metaTileEntity.hatch.gui.GT_Container_Rack;
import com.github.technus.tectech.thing.metaTileEntity.hatch.gui.GT_GUIContainer_Rack;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.GT_MetaTileEntity_Hatch;
import gregtech.api.objects.GT_RenderedTexture;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.util.HashMap;
import java.util.Map;

import static com.github.technus.tectech.util.CommonValues.MULTI_CHECK_AT;
import static com.github.technus.tectech.util.Util.getUniqueIdentifier;
import static com.github.technus.tectech.loader.TecTechConfig.DEBUG_MODE;
import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

/**
 * Created by Tec on 03.04.2017.
 */
public class GT_MetaTileEntity_Hatch_Rack extends GT_MetaTileEntity_Hatch {
    private static Textures.BlockIcons.CustomIcon EM_R;
    private static Textures.BlockIcons.CustomIcon EM_R_ACTIVE;
    public int heat = 0;
    private float overClock = 1, overVolt = 1;
    private static Map<String, RackComponent> componentBinds = new HashMap<>();

    private String clientLocale = "en_US";

    public GT_MetaTileEntity_Hatch_Rack(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 4, "");
        Util.setTier(aTier,this);
    }

    public GT_MetaTileEntity_Hatch_Rack(String aName, int aTier, String aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 4, aDescription, aTextures);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("eHeat", heat);
        aNBT.setFloat("eOverClock", overClock);
        aNBT.setFloat("eOverVolt", overVolt);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        heat = aNBT.getInteger("eHeat");
        overClock = aNBT.getFloat("eOverClock");
        overVolt = aNBT.getFloat("eOverVolt");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        super.registerIcons(aBlockIconRegister);
        EM_R_ACTIVE = new Textures.BlockIcons.CustomIcon("iconsets/EM_RACK_ACTIVE");
        EM_R = new Textures.BlockIcons.CustomIcon("iconsets/EM_RACK");
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[]{aBaseTexture, new GT_RenderedTexture(EM_R_ACTIVE)};
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[]{aBaseTexture, new GT_RenderedTexture(EM_R)};
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new GT_MetaTileEntity_Hatch_Rack(mName, mTier, mDescription, mTextures);
    }

    @Override
    public boolean isSimpleMachine() {
        return true;
    }

    @Override
    public boolean isFacingValid(byte aFacing) {
        return aFacing >= 2;
    }

    @Override
    public boolean isAccessAllowed(EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return true;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, byte aSide, ItemStack aStack) {
        if (aBaseMetaTileEntity.isActive() || heat > 500) {
            return false;
        }
        return aSide == aBaseMetaTileEntity.getFrontFacing();
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, byte aSide, ItemStack aStack) {
        if (aBaseMetaTileEntity.isActive() || heat > 500) {
            return false;
        }
        return aSide == aBaseMetaTileEntity.getFrontFacing();
    }

    @Override
    public Object getServerGUI(int aID, InventoryPlayer aPlayerInventory, IGregTechTileEntity aBaseMetaTileEntity) {
        return new GT_Container_Rack(aPlayerInventory, aBaseMetaTileEntity);
    }

    @Override
    public Object getClientGUI(int aID, InventoryPlayer aPlayerInventory, IGregTechTileEntity aBaseMetaTileEntity) {
        return new GT_GUIContainer_Rack(aPlayerInventory, aBaseMetaTileEntity, translateToLocal("gt.blockmachines.hatch.rack.tier.08.name"));//Computer Rack
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aBaseMetaTileEntity.isClientSide()) {
            return true;
        }
        try {
            EntityPlayerMP player = (EntityPlayerMP) aPlayer;
            clientLocale = (String) FieldUtils.readField(player, "translator", true);
        } catch (Exception e) {
            clientLocale = "en_US";
        }
        //if(aBaseMetaTileEntity.isActive())
        //    aPlayer.addChatComponentMessage(new ChatComponentText("It is still active..."));
        //else if(heat>0)
        //    aPlayer.addChatComponentMessage(new ChatComponentText("It is still warm..."));
        //else
        aBaseMetaTileEntity.openGUI(aPlayer);
        return true;
    }

    private int getComputationPower(float overclock, float overvolt, boolean tickingComponents) {
        float computation = 0, heat = 0;
        for (int i = 0; i < mInventory.length; i++) {
            if (mInventory[i] == null || mInventory[i].stackSize != 1) {
                continue;
            }
            RackComponent comp = componentBinds.get(getUniqueIdentifier(mInventory[i]));
            if (comp == null) {
                continue;
            }
            if (tickingComponents) {
                if (this.heat > comp.maxHeat) {
                    mInventory[i] = null;
                    continue;
                } else if (comp.subZero || this.heat >= 0) {
                    heat += (1f + comp.coEff * this.heat / 10000f) * (comp.heat > 0 ? comp.heat * overclock * overclock * overvolt : comp.heat);
                    //=MAX(0;MIN(MIN($B4;1*C$3+C$3-0,25);1+RAND()+(C$3-1)-($B4-1)/2))
                    if (overvolt * 10f > 7f + TecTech.RANDOM.nextFloat()) {
                        computation += comp.computation * Math.max(0, Math.min(Math.min(overclock, overvolt + overvolt - 0.25), 1 + TecTech.RANDOM.nextFloat() + (overvolt - 1) - (overclock - 1) / 2));
                    }
                }
            } else {
                computation += comp.computation * overclock;
            }
        }
        if (tickingComponents) {
            this.heat += Math.ceil(heat);
        }
        return (int) Math.floor(computation);
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    public int tickComponents(float oc, float ov) {
        if (oc > 3 + TecTech.RANDOM.nextFloat() || ov > 2 + TecTech.RANDOM.nextFloat()) {
            getBaseMetaTileEntity().setToFire();
        }
        overClock = oc;
        overVolt = ov;
        return getComputationPower(overClock, overVolt, true);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide()) {
            if (aTick % 20 == MULTI_CHECK_AT) {
                if (heat > 0) {
                    float heatC = 0;
                    for (int i = 0; i < mInventory.length; i++) {
                        if (mInventory[i] == null || mInventory[i].stackSize != 1) {
                            continue;
                        }
                        RackComponent comp = componentBinds.get(getUniqueIdentifier(mInventory[i]));
                        if (comp == null) {
                            continue;
                        }
                        if (heat > comp.maxHeat) {
                            mInventory[i] = null;
                        } else if (comp.heat < 0) {
                            heatC += comp.heat * (heat / 10000f);
                        }
                    }
                    heat += Math.max(-heat, Math.ceil(heatC));
                }

                if (heat > 0) {
                    heat -= Math.max(heat / 1000, 1);
                } else if (heat < 0) {
                    heat -= Math.min(heat / 1000, -1);
                }

                if (heat > 10000) {
                    aBaseMetaTileEntity.setToFire();
                } else if (heat > 9000) {
                    aBaseMetaTileEntity.setOnFire();
                } else if (heat < -10000) {
                    heat = -10000;
                }
            }
        }
    }

    //@Override
    //public void onRemoval() {
    //    if(mInventory!=null && (heat>0 || (getBaseMetaTileEntity()!=null && getBaseMetaTileEntity().isActive())))
    //        for(int i=0;i<mInventory.length;i++)
    //            mInventory[i]=null;
    //}

    @Override
    public int getSizeInventory() {//HACK TO NOT DROP CONTENTS!!!
        return heat > 500 || getBaseMetaTileEntity().isActive() ? 0 : mInventory.length;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                CommonValues.TEC_MARK_EM,
                translateToLocal("gt.blockmachines.hatch.rack.desc.0"),//4 Slot Rack
                EnumChatFormatting.AQUA + translateToLocal("gt.blockmachines.hatch.rack.desc.1")//Holds Computer Components
        };
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return new String[]{
                translateToLocalFormatted("tt.keyphrase.Base_computation", clientLocale) + ": " + EnumChatFormatting.AQUA + getComputationPower(1, 0, false),
                translateToLocalFormatted("tt.keyphrase.After_overclocking", clientLocale) + ": " + EnumChatFormatting.AQUA + getComputationPower(overClock, 0, false),
                translateToLocalFormatted("tt.keyphrase.Heat_Accumulated", clientLocale) + ": " + EnumChatFormatting.RED + (heat + 99) / 100 + EnumChatFormatting.RESET + " %"
        };
        //heat==0? --> ((heat+9)/10) = 0
        //Heat==1-10? -->  1
    }

    public static void run() {//20k heat cap max!
        new RackComponent(ItemList.Circuit_Primitive.get(1), 1, 4, 0, 500, true);//Primitive Circuit
        new RackComponent(ItemList.Circuit_Basic.get(1), 4, 8, 0, 1000, true);//Basic Circuit
        new RackComponent(ItemList.Circuit_Microprocessor.get(1), 6, 8, 0, 1250, true);
        new RackComponent(ItemList.Circuit_Good.get(1), 6, 9, -.05f, 1500, true);//Good Circuit
        new RackComponent(ItemList.Circuit_Integrated_Good.get(1), 7, 9, -.075f, 1750, true);
        new RackComponent(ItemList.Circuit_Processor.get(1), 8, 9, -.07f, 1800, true);
        new RackComponent(ItemList.Circuit_Parts_Advanced.get(1), 1, 2, -.05f, 2000, true);
        new RackComponent(ItemList.Circuit_Nanoprocessor.get(1), 8, 10, -.09f, 2250, true);//Advanced Circuit
        new RackComponent(ItemList.Circuit_Advanced.get(1), 8, 10, -.1f, 2500, true);
        new RackComponent(ItemList.Circuit_Data.get(1), 9, 1, -.1f, 3000, true);//EV Circuit
        new RackComponent(ItemList.Circuit_Nanocomputer.get(1), 11, 10, -.125f, 3300, true);
        new RackComponent(ItemList.Circuit_Quantumprocessor.get(1), 13, 10, -.15f, 3600, true);
        new RackComponent(ItemList.Circuit_Elite.get(1), 12, 10, -.15F, 3500, true);//IV Circuit
        new RackComponent(ItemList.Circuit_Elitenanocomputer.get(1), 14, 10, -.15F, 4000, true);
        new RackComponent(ItemList.Circuit_Quantumcomputer.get(1), 16, 10, -.15F, 4500, true);
        new RackComponent(ItemList.Circuit_Crystalprocessor.get(1), 18, 10, -.15F, 5000, true);
        new RackComponent(ItemList.Circuit_Master.get(1), 16, 12, -.2F, 5000, true);//LuV Circuit
        new RackComponent(ItemList.Circuit_Masterquantumcomputer.get(1), 16, 13, -.2F, 5100, true);
        new RackComponent(ItemList.Circuit_Crystalcomputer.get(1), 20, 14, -.25F, 5200, true);
        new RackComponent(ItemList.Circuit_Neuroprocessor.get(1), 24, 15, -.3F, 5300, true);
        new RackComponent(ItemList.Circuit_Quantummainframe.get(1), 22, 14, -.3F, 5200, true);//ZPM Circuit
        new RackComponent(ItemList.Circuit_Ultimatecrystalcomputer.get(1), 26, 16, -.3F, 5400, true);
        new RackComponent(ItemList.Circuit_Wetwarecomputer.get(1), 30, 18, -.3F, 5600, true);
        new RackComponent(ItemList.Circuit_Crystalmainframe.get(1), 30, 18, -.35F, 5500, true);//UV Circuit
        new RackComponent(ItemList.Circuit_Wetwaresupercomputer.get(1), 35, 22, -.3F, 5700, true);
        new RackComponent(ItemList.Circuit_Wetwaremainframe.get(1), 38, 25, -.4F, 6000, true);//UHV Circuit
        
        new RackComponent("IC2:ic2.reactorVent", 0, -1, 10f, 1000, false);
        new RackComponent("IC2:ic2.reactorVentCore", 0, -1, 20f, 2500, false);
        new RackComponent("IC2:ic2.reactorVentGold", 0, -1, 40f, 5000, false);
        new RackComponent("IC2:ic2.reactorVentDiamond", 0, -1, 80f, 10000, false);//2x oc
        
        if (Loader.isModLoaded(Reference.DREAMCRAFT)) {
            //GTNH-GT5u circuits
            //these components causes crashes when used with the original GT5u
            new RackComponent(ItemList.NandChip.get(1), 2, 6, 0, 750, true);//Primitive Circuit
            new RackComponent(ItemList.Circuit_Biowarecomputer.get(1), 40, 26, -.35F, 5900, true);
            new RackComponent(ItemList.Circuit_Biowaresupercomputer.get(1), 42, 30, -.4F, 6200, true);
            new RackComponent(ItemList.Circuit_Biomainframe.get(1), 40, 28, -.4F, 6000, true);//UHV Circuit
            new RackComponent(ItemList.Circuit_Bioprocessor.get(1), 34, 20, -.35F, 5800, true); 
            
            new RackComponent("dreamcraft:item.HighEnergyCircuitParts", 3, 2, -.1f, 9001, true);
            new RackComponent("dreamcraft:item.HighEnergyFlowCircuit", 24, 16, -.25f, 10000, true);
            new RackComponent("dreamcraft:item.NanoCircuit", 48, 35, -.45f, 8000, true);
            new RackComponent("dreamcraft:item.PikoCircuit", 64, 40, -.5f, 8500, true);
            new RackComponent("dreamcraft:item.QuantumCircuit", 128, 48, -.6f, 9000, true);
        }
        
        if (Loader.isModLoaded(Reference.SPARTAKCORE)) {
            //CustomGT5u circuits
            //these components causes crashes when used with the original GT5u
            new RackComponent(ItemList.NandChip.get(1), 2, 6, 0, 750, true);//Primitive Circuit
            new RackComponent(ItemList.Circuit_Biowarecomputer.get(1), 40, 26, -.35F, 5900, true);
            new RackComponent(ItemList.Circuit_Biowaresupercomputer.get(1), 42, 30, -.4F, 6200, true);
            new RackComponent(ItemList.Circuit_Biomainframe.get(1), 40, 28, -.4F, 6000, true);//UHV Circuit
            new RackComponent(ItemList.Circuit_Bioprocessor.get(1), 34, 20, -.35F, 5800, true); 
        }
        
        if (Loader.isModLoaded("OpenComputers")) {
            new RackComponent("OpenComputers:item.oc.Transistor", 0, 1, 0f, 100, true);//Transistor
            new RackComponent("OpenComputers:item.oc.Microchip0", 7, 12, -.05f, 1500, true);//chip t1
            new RackComponent("OpenComputers:item.oc.Microchip1", 18, 20, -.1f, 3000, true);//chip t2
            new RackComponent("OpenComputers:item.oc.Microchip2", 25, 22, -.15f, 4500, true);//chip t3
            new RackComponent("OpenComputers:item.oc.ALU", 10, 15, -.05f, 3000, true);//alu
            new RackComponent("OpenComputers:item.oc.ControlUnit", 25, 18, -.05f, 1500, true);//cu

            new RackComponent("OpenComputers:item.oc.ComponentBus0", 42, 30, -.05f, 1500, true);//bus t1
            new RackComponent("OpenComputers:item.oc.ComponentBus1", 70, 50, -.1f, 3000, true);//bus t2
            new RackComponent("OpenComputers:item.oc.ComponentBus2", 105, 72, -.15f, 4500, true);//bus t3

            new RackComponent("OpenComputers:item.oc.CPU0", 106, 73, -.1f, 1500, true);//cpu t1
            new RackComponent("OpenComputers:item.oc.CPU1", 226, 153, -.15f, 3000, true);//cpu t2
            new RackComponent("OpenComputers:item.oc.CPU2", 374, 241, -.2f, 4500, true);//cpu t3

            new RackComponent("OpenComputers:item.oc.GraphicsCard0", 20, 27, -.1f, 1500, true);//gpu t1
            new RackComponent("OpenComputers:item.oc.GraphicsCard1", 62, 67, -.2f, 3000, true);//gpu t2
            new RackComponent("OpenComputers:item.oc.GraphicsCard2", 130, 111, -.3f, 4500, true);//gpu t3

            new RackComponent("OpenComputers:item.oc.APU0", 350, 234, -.1f, 1500, true);//apu t2
            new RackComponent("OpenComputers:item.oc.APU1", 606, 398, -.2f, 4500, true);//apu t3
            new RackComponent("OpenComputers:item.oc.APU2", 1590, 1006, -.3f, 9000, true);//apu tC
        }
    }

    public static class RackComponent implements Comparable<RackComponent> {
        private final String unlocalizedName;
        private final float heat, coEff, computation, maxHeat;
        private final boolean subZero;

        RackComponent(ItemStack is, float computation, float heat, float coEff, float maxHeat, boolean subZero) {
            this(getUniqueIdentifier(is), computation, heat, coEff, maxHeat, subZero);
        }

        RackComponent(String is, float computation, float heat, float coEff, float maxHeat, boolean subZero) {
            unlocalizedName = is;
            this.heat = heat;
            this.coEff = coEff;
            this.computation = computation;
            this.maxHeat = maxHeat;
            this.subZero = subZero;
            componentBinds.put(unlocalizedName, this);
            if (DEBUG_MODE) {
                TecTech.LOGGER.info("Component registered: " + unlocalizedName);
            }
        }

        @Override
        public int compareTo(RackComponent o) {
            return unlocalizedName.compareTo(o.unlocalizedName);
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof RackComponent) {
                return compareTo((RackComponent) obj) == 0;
            }
            return false;
        }
    }
}


