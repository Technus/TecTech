package com.github.technus.tectech.mechanics.elementalMatter.definitions.complex;

import com.github.technus.tectech.Reference;
import com.github.technus.tectech.TecTech;
import com.github.technus.tectech.compatibility.gtpp.GtppAtomLoader;
import com.github.technus.tectech.mechanics.elementalMatter.core.cElementalDecay;
import com.github.technus.tectech.mechanics.elementalMatter.core.cElementalDefinitionStackMap;
import com.github.technus.tectech.mechanics.elementalMatter.core.cElementalMutableDefinitionStackMap;
import com.github.technus.tectech.mechanics.elementalMatter.core.stacks.cElementalDefinitionStack;
import com.github.technus.tectech.mechanics.elementalMatter.core.tElementalException;
import com.github.technus.tectech.mechanics.elementalMatter.core.templates.cElementalDefinition;
import com.github.technus.tectech.mechanics.elementalMatter.core.templates.iElementalDefinition;
import com.github.technus.tectech.mechanics.elementalMatter.core.transformations.aFluidDequantizationInfo;
import com.github.technus.tectech.mechanics.elementalMatter.core.transformations.aItemDequantizationInfo;
import com.github.technus.tectech.mechanics.elementalMatter.core.transformations.aOredictDequantizationInfo;
import com.github.technus.tectech.mechanics.elementalMatter.core.transformations.bTransformationInfo;
import com.github.technus.tectech.mechanics.elementalMatter.definitions.primitive.eBosonDefinition;
import com.github.technus.tectech.mechanics.elementalMatter.definitions.primitive.eLeptonDefinition;
import com.github.technus.tectech.mechanics.elementalMatter.definitions.primitive.eNeutrinoDefinition;
import com.github.technus.tectech.util.Util;
import com.github.technus.tectech.util.XSTR;
import cpw.mods.fml.common.Loader;
import gregtech.api.enums.Materials;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

import static com.github.technus.tectech.compatibility.thaumcraft.elementalMatter.definitions.dComplexAspectDefinition.getNbtTagCompound;
import static com.github.technus.tectech.loader.TecTechConfig.DEBUG_MODE;
import static com.github.technus.tectech.mechanics.elementalMatter.core.transformations.bTransformationInfo.AVOGADRO_CONSTANT_144;
import static com.github.technus.tectech.mechanics.elementalMatter.definitions.primitive.eBosonDefinition.boson_Y__;
import static com.github.technus.tectech.mechanics.elementalMatter.definitions.primitive.eBosonDefinition.deadEnd;
import static com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_scanner.*;
import static com.github.technus.tectech.util.XSTR.XSTR_INSTANCE;
import static gregtech.api.enums.OrePrefixes.dust;

/**
 * Created by danie_000 on 18.11.2016.
 */
public final class dAtomDefinition extends cElementalDefinition {
    public static final long ATOM_COMPLEXITY_LIMIT=65536L;
    private static final byte BYTE_OFFSET=32;

    private final int hash;
    public static final bTransformationInfo TRANSFORMATION_INFO = new bTransformationInfo(16,0,64);
    public static double refMass, refUnstableMass;

    private static final byte nbtType = (byte) 'a';
    private static final Random xstr = new XSTR();//NEEDS SEPARATE!
    private static Map<Integer, TreeSet<Integer>> stableIsotopes = new HashMap<>();
    private static final Map<Integer, dAtomDefinition> stableAtoms = new HashMap<>();
    private static Map<Integer, TreeMap<Double, Integer>> mostStableUnstableIsotopes = new HashMap<>();
    private static final Map<Integer, dAtomDefinition> unstableAtoms = new HashMap<>();
    private static cElementalDefinitionStack alpha,deuterium,tritium,helium_3,beryllium_8,carbon_14,neon_24,silicon_34;
    private static final HashMap<dAtomDefinition,Double> lifetimeOverrides = new HashMap<>();

    public final iaeaNuclide iaea;

    private static dAtomDefinition somethingHeavy;
    public static dAtomDefinition getSomethingHeavy() {
        return somethingHeavy;
    }

    private static final ArrayList<Runnable> overrides = new ArrayList<>();
    public static void addOverride(dAtomDefinition atom, double rawLifeTime){
        lifetimeOverrides.put(atom,rawLifeTime);
    }

    //float-mass in eV/c^2
    public final double mass;
    //public final int charge;
    public final int charge;
    //int -electric charge in 1/3rds of electron charge for optimization
    public final int chargeLeptons;
    private double rawLifeTime;
    //generation max present inside - minus if contains any anti quark
    public final byte type;

    public final byte decayMode;//t neutron to proton+,0,f proton to neutron
    //public final boolean stable;

    public final int neutralCount;
    public final int element;

    private final boolean iaeaDefinitionExistsAndHasEnergyLevels;

    private final cElementalDefinitionStackMap elementalStacks;

    //stable is rawLifeTime>=10^9

    @Deprecated
    public dAtomDefinition(iElementalDefinition... things) throws tElementalException {
        this(true, new cElementalDefinitionStackMap(things));
    }

    @Deprecated
    private dAtomDefinition(boolean check, iElementalDefinition... things) throws tElementalException {
        this(check, new cElementalDefinitionStackMap(things));
    }

    public dAtomDefinition(cElementalDefinitionStack... things) throws tElementalException {
        this(true, new cElementalDefinitionStackMap(things));
    }

    private dAtomDefinition(boolean check, cElementalDefinitionStack... things) throws tElementalException {
        this(check, new cElementalDefinitionStackMap(things));
    }

    public dAtomDefinition(cElementalDefinitionStackMap things) throws tElementalException {
        this(true, things);
    }

    private dAtomDefinition(boolean check, cElementalDefinitionStackMap things) throws tElementalException {
        if (check && !canTheyBeTogether(things)) {
            throw new tElementalException("Atom Definition error");
        }
        elementalStacks = things;

        double mass = 0;
        int cLeptons = 0;
        int cNucleus = 0;
        int neutralCount = 0, element = 0;
        int type = 0;
        boolean containsAnti = false;
        for (cElementalDefinitionStack stack : elementalStacks.values()) {
            iElementalDefinition def = stack.definition;
            int amount = (int)stack.amount;
            if((int)stack.amount!=stack.amount){
                throw new ArithmeticException("Amount cannot be safely converted to int!");
            }
            mass += stack.getMass();
            if (def.getType() < 0) {
                containsAnti = true;
            }
            type = Math.max(type, Math.abs(def.getType()));

            if (def instanceof eLeptonDefinition) {
                cLeptons += stack.getCharge();
            } else {
                cNucleus += stack.getCharge();
                if (def.getCharge() == 3) {
                    element += amount;
                } else if (def.getCharge() == -3) {
                    element -= amount;
                } else if (def.getCharge() == 0) {
                    neutralCount += amount;
                }
            }
        }
        this.type = containsAnti ? (byte) -type : (byte) type;
        //this.mass = mass;
        chargeLeptons = cLeptons;
        charge = cNucleus + cLeptons;
        this.neutralCount = neutralCount;
        this.element = element;

        element = Math.abs(element);

        //stability curve
        int StableIsotope = stableIzoCurve(element);
        int izoDiff = neutralCount - StableIsotope;
        int izoDiffAbs = Math.abs(izoDiff);

        xstr.setSeed((element + 1L) * (neutralCount + 100L));
        iaea =iaeaNuclide.get(element,neutralCount);
        if(iaea!=null){
            if(Double.isNaN(iaea.mass)) {
                this.mass = mass;
            } else {
                this.mass = iaea.mass;
            }

            if(Double.isNaN(iaea.halfTime)) {
                Double overriddenLifeTime= lifetimeOverrides.get(this);
                double rawLifeTimeTemp;
                if(overriddenLifeTime!=null) {
                    rawLifeTimeTemp = overriddenLifeTime;
                } else {
                    rawLifeTimeTemp = calculateLifeTime(izoDiff, izoDiffAbs, element, neutralCount, containsAnti);
                }
                rawLifeTime = Math.min(rawLifeTimeTemp, STABLE_RAW_LIFE_TIME);
            }else {
                rawLifeTime = containsAnti ? iaea.halfTime * 1.5514433E-21d * (1d + xstr.nextDouble() * 9d) : iaea.halfTime;
            }
            iaeaDefinitionExistsAndHasEnergyLevels =iaea.energeticStatesArray.length>1;
        }else{
            this.mass=mass;

            Double overriddenLifeTime= lifetimeOverrides.get(this);
            double rawLifeTimeTemp;
            if(overriddenLifeTime!=null) {
                rawLifeTimeTemp = overriddenLifeTime;
            } else {
                rawLifeTimeTemp = calculateLifeTime(izoDiff, izoDiffAbs, element, neutralCount, containsAnti);
            }
            rawLifeTime = Math.min(rawLifeTimeTemp, STABLE_RAW_LIFE_TIME);

            iaeaDefinitionExistsAndHasEnergyLevels =false;
        }

        if(iaea==null || iaea.energeticStatesArray[0].energy!=0) {
            if (izoDiff == 0) {
                decayMode = 0;
            } else {
                decayMode = izoDiff > 0 ? (byte) Math.min(2, 1 + izoDiffAbs / 4) : (byte) -Math.min(2, 1 + izoDiffAbs / 4);
            }
        }else{
            decayMode = izoDiff > 0 ? (byte) (Math.min(2, 1 + izoDiffAbs / 4)+ BYTE_OFFSET) : (byte) (-Math.min(2, 1 + izoDiffAbs / 4) + BYTE_OFFSET);
        }
        //this.stable = this.rawLifeTime >= STABLE_RAW_LIFE_TIME;
        hash=super.hashCode();
    }

    private static int stableIzoCurve(int element) {
        return (int) Math.round(-1.19561E-06D * Math.pow(element, 4D) +
                1.60885E-04D * Math.pow(element, 3D) +
                3.76604E-04D * Math.pow(element, 2D) +
                1.08418E+00D * (double) element);
    }

    private static double calculateLifeTime(int izoDiff, int izoDiffAbs, int element, int isotope, boolean containsAnti) {
        double rawLifeTime;

        if (element <= 83 && isotope < 127 && (izoDiffAbs == 0 || element == 1 && isotope == 0 || element == 2 && isotope == 1 || izoDiffAbs == 1 && element > 2 && element % 2 == 1 || izoDiffAbs == 3 && element > 30 && element % 2 == 0 || izoDiffAbs == 5 && element > 30 && element % 2 == 0 || izoDiffAbs == 2 && element > 20 && element % 2 == 1)) {
            rawLifeTime = (1D + xstr.nextDouble() * 9D) * (containsAnti ? 2.381e4D : 1.5347e25D);
        } else {
            //Y = (X-A)/(B-A) * (D-C) + C
            double unstabilityEXP;
            if (element == 0) {
                return 1e-35D;
            } else if (element == 1) {
                unstabilityEXP = 1.743D - Math.abs(izoDiff - 1) * 9.743D;
            } else if (element == 2) {
                switch (isotope) {
                    case 4:
                        unstabilityEXP = 1.61D;
                        break;
                    case 5:
                        unstabilityEXP = -7.523D;
                        break;
                    case 6:
                        unstabilityEXP = -1.51D;
                        break;
                    default:
                        unstabilityEXP = -(izoDiffAbs * 6.165D);
                        break;
                }
            } else if (element <= 83 || isotope <= 127 && element <= 120) {
                double elementPow4 = Math.pow(element, 4);

                unstabilityEXP = Math.min(element / 2.4D, 6 + ((element + 1) % 2) * 3e6D / elementPow4) + -izoDiff * elementPow4 / 1e8D - Math.abs(izoDiff - 1 + element / 60D) * (3D - element / 12.5D + element * element / 1500D);
            } else if (element < 180) {
                unstabilityEXP = Math.min((element - 85) * 2, 16 + ((isotope + 1) % 2) * 2.5D - (element - 85) / 3D) - Math.abs(izoDiff) * (3D - element / 13D + element * element / 1600D);
            } else {
                return -1;
            }
            if ((isotope == 127 || isotope == 128) && element < 120 && element > 83) {
                unstabilityEXP -= 1.8D;
            }
            if (element > 83 && element < 93 && isotope % 2 == 0 && izoDiff == 3) {
                unstabilityEXP += 6;
            }
            if (element > 93 && element < 103 && isotope % 2 == 0 && izoDiff == 4) {
                unstabilityEXP += 6;
            }
            rawLifeTime = (containsAnti ? 1e-8D : 1) * Math.pow(10D, unstabilityEXP) * (1D + xstr.nextDouble() * 9D);
        }

        if (rawLifeTime < 8e-15D) {
            return 1e-35D;
        }
        if (rawLifeTime > 8e28D) {
            return 8e30D;
        }
        return rawLifeTime;
    }

    private static boolean canTheyBeTogether(cElementalDefinitionStackMap stacks) {
        boolean nuclei = false;
        long qty=0;
        for (cElementalDefinitionStack stack : stacks.values()) {
            if (stack.definition instanceof dHadronDefinition) {
                if (((dHadronDefinition) stack.definition).amount != 3) {
                    return false;
                }
                nuclei = true;
            } else if (!(stack.definition instanceof eLeptonDefinition)) {
                return false;
            }
            if((int)stack.amount!=stack.amount){
                throw new ArithmeticException("Amount cannot be safely converted to int!");
            }
            qty+=stack.amount;
        }
        return nuclei && qty<ATOM_COMPLEXITY_LIMIT;
    }

    @Override
    public int getCharge() {
        return charge;
    }

    public int getChargeLeptons() {
        return chargeLeptons;
    }

    public int getChargeHadrons() {
        return charge - chargeLeptons;
    }

    public int getIonizationElementWise() {
        return element * 3 + chargeLeptons;
    }

    @Override
    public double getMass() {
        return mass;
    }

    @Override
    public byte getType() {
        return type;
    }

    @Override
    public double getRawTimeSpan(long currentEnergy) {
        if(currentEnergy<=0) {
            return rawLifeTime;
        }
        if(iaeaDefinitionExistsAndHasEnergyLevels){
            if(currentEnergy>=iaea.energeticStatesArray.length){
                return iaea.energeticStatesArray[iaea.energeticStatesArray.length-1].Thalf/(currentEnergy-iaea.energeticStatesArray.length+1);
            }
            return iaea.energeticStatesArray[(int)currentEnergy].Thalf;
        }
        return rawLifeTime/(currentEnergy+1);
    }

    @Override
    public boolean isTimeSpanHalfLife() {
        return true;
    }

    @Override
    public byte getColor() {
        return -10;
    }

    @Override
    public String getName() {
        int element = Math.abs(this.element);
        boolean negative = this.element < 0;
        try {
            if (Math.abs(type) != 1) {
                return (negative ? "~? " : "? ") + Nomenclature.NAME[element];
            }
            return negative ? '~' + Nomenclature.NAME[element] : Nomenclature.NAME[element];
        } catch (Exception e) {
            if (DEBUG_MODE) {
                e.printStackTrace();
            }
            return (negative ? "Element: ~" : "Element: ") + element;
        }
    }

    @Override
    public String getSymbol() {
        int element = Math.abs(this.element);
        boolean negative = this.element < 0;
        try {
            return (negative ? "~" : "") + Nomenclature.SYMBOL[element] + " N:" + neutralCount + " I:" + (neutralCount+element) + " C:" + getCharge();
        } catch (Exception e) {
            if (DEBUG_MODE) {
                e.printStackTrace();
            }
            try {
                int s100 = element / 100, s1 = element / 10 % 10, s10 = element % 10;
                return (negative ? "~" : "") + Nomenclature.SYMBOL_IUPAC[10 + s100] + Nomenclature.SYMBOL_IUPAC[s10] + Nomenclature.SYMBOL_IUPAC[s1] + " N:" + neutralCount + " I:" + (neutralCount+element) + " C:" + getCharge();
            } catch (Exception E) {
                if (DEBUG_MODE) {
                    e.printStackTrace();
                }
                return (negative ? "~" : "") + "? N:" + neutralCount + " I:" + (neutralCount+element) + " C:" + getCharge();
            }
        }
    }

    @Override
    public String getShortSymbol() {
        int element = Math.abs(this.element);
        boolean negative = this.element < 0;
        try {
            return (negative ? "~" : "") + Nomenclature.SYMBOL[element];
        } catch (Exception e) {
            if (DEBUG_MODE) {
                e.printStackTrace();
            }
            try {
                int s100 = element / 100, s1 = element / 10 % 10, s10 = element % 10;
                return (negative ? "~" : "") + Nomenclature.SYMBOL_IUPAC[10 + s100] + Nomenclature.SYMBOL_IUPAC[s10] + Nomenclature.SYMBOL_IUPAC[s1];
            } catch (Exception E) {
                if (DEBUG_MODE) {
                    e.printStackTrace();
                }
                return (negative ? "~" : "") + "?";
            }
        }
    }

    @Override
    public cElementalDefinitionStackMap getSubParticles() {
        return elementalStacks.clone();
    }

    @Override
    public cElementalDecay[] getDecayArray() {
        ArrayList<cElementalDecay> decaysList=new ArrayList<>(4);
        return getDecayArray(decaysList,decayMode,true);
    }

    private cElementalDecay[] getDecayArray(ArrayList<cElementalDecay> decaysList,int decayMode,boolean tryAnti) {//todo?
        if (type == 1) {
            switch (decayMode) {
                case -2:
                    if(TecTech.RANDOM.nextBoolean() && ElectronCapture(decaysList)) {
                        return decaysList.toArray(cElementalDecay.noProduct);
                    } else if(PbetaDecay(decaysList)) {
                        return decaysList.toArray(cElementalDecay.noProduct);
                    }
                    break;
                case -1:
                    if(Emmision(decaysList, dHadronDefinition.hadron_p1)) {
                        return decaysList.toArray(cElementalDecay.noProduct);
                    }
                    break;
                case 0:
                    if(alphaDecay(decaysList)) {
                        return decaysList.toArray(cElementalDecay.noProduct);
                    }
                    break;
                case 1:
                    if(Emmision(decaysList, dHadronDefinition.hadron_n1)) {
                        return decaysList.toArray(cElementalDecay.noProduct);
                    }
                    break;
                case 2:
                    if(MbetaDecay(decaysList)) {
                        return decaysList.toArray(cElementalDecay.noProduct);
                    }
                    break;
                default:
                    if(decayMode>8){
                        if(iaeaDecay(decaysList,0)) {
                            return decaysList.toArray(cElementalDecay.noProduct);
                        }
                        return getDecayArray(decaysList,decayMode- BYTE_OFFSET,false);
                    }
            }
            return cElementalDecay.noDecay;
        }else if(type ==-1){
            dAtomDefinition anti=getAnti();
            if(anti!=null) {
                return anti.getDecayArray(decaysList, decayMode, false);
            }
        }
        return getNaturalDecayInstant();
    }

    private boolean iaeaDecay(ArrayList<cElementalDecay> decaysList,long energy){
        iaeaNuclide.energeticState state;
        if(energy>iaea.energeticStatesArray.length) {
            state = iaea.energeticStatesArray[iaea.energeticStatesArray.length - 1];
        } else if(energy<=0) {
            state = iaea.energeticStatesArray[0];
        } else {
            state = iaea.energeticStatesArray[(int) energy];
        }
        for (int i=0;i<state.decaymodes.length;i++){
            if(!getDecayFromIaea(decaysList,state.decaymodes[i],energy)) {
                decaysList.clear();
                return false;
            }
        }
        return !decaysList.isEmpty();
    }

    private boolean getDecayFromIaea(ArrayList<cElementalDecay> decaysList, iaeaNuclide.iaeaDecay decay, long energy){
        cElementalMutableDefinitionStackMap withThis=elementalStacks.toMutable(),newStuff=new cElementalMutableDefinitionStackMap();
        switch (decay.decayName){
            case "D": {
                if (withThis.removeAllAmounts(false, deuterium.definition.getSubParticles())){
                    withThis.putReplace(deuterium);
                    decaysList.add(new cElementalDecay(decay.chance,withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                    return true;
                }
            } break;
            case "3H": {
                if (withThis.removeAllAmounts(false, tritium.definition.getSubParticles())){
                    withThis.putReplace(tritium);
                    decaysList.add(new cElementalDecay(decay.chance,withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                    return true;
                }
            } break;
            case "3HE": {
                if (withThis.removeAllAmounts(false, helium_3.definition.getSubParticles())){
                    withThis.putReplace(helium_3);
                    decaysList.add(new cElementalDecay(decay.chance,withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                    return true;
                }
            } break;
            case "8BE": {
                if (withThis.removeAllAmounts(false, beryllium_8.definition.getSubParticles())){
                    withThis.putReplace(beryllium_8);
                    decaysList.add(new cElementalDecay(decay.chance,withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                    return true;
                }
            } break;
            case "14C": {
                if (withThis.removeAllAmounts(false, carbon_14.definition.getSubParticles())){
                    newStuff.putReplace(carbon_14);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "24NE": {
                if (withThis.removeAllAmounts(false, neon_24.definition.getSubParticles())){
                    newStuff.putReplace(neon_24);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "34SI": {
                if (withThis.removeAllAmounts(false, silicon_34.definition.getSubParticles())){
                    newStuff.putReplace(silicon_34);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "A": case "A?": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_n2,dHadronDefinition.hadron_p2)){
                    newStuff.putReplace(alpha);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "B+": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p1)){
                    withThis.putUnify(dHadronDefinition.hadron_n1);
                    newStuff.putReplace(eLeptonDefinition.lepton_e_1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve1);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "2B+": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p2)){
                    withThis.putUnify(dHadronDefinition.hadron_n2);
                    newStuff.putReplace(eLeptonDefinition.lepton_e_2);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve2);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "B-": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_n1)){
                    withThis.putUnify(dHadronDefinition.hadron_p1);
                    newStuff.putReplace(eLeptonDefinition.lepton_e1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve_1);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "2B-": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_n2)){
                    withThis.putUnify(dHadronDefinition.hadron_p2);
                    newStuff.putReplace(eLeptonDefinition.lepton_e2);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve_2);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "EC": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p1,eLeptonDefinition.lepton_e1)){
                    withThis.putUnify(dHadronDefinition.hadron_n1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve1);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "2EC": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p2,eLeptonDefinition.lepton_e2)){
                    withThis.putUnify(dHadronDefinition.hadron_n2);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve2);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "B++EC": case "EC+B+": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p2,eLeptonDefinition.lepton_e1)){
                    withThis.putUnify(dHadronDefinition.hadron_n2);
                    newStuff.putReplace(eLeptonDefinition.lepton_e_1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve2);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "B+A": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p3, dHadronDefinition.hadron_n1)){
                    newStuff.putReplace(eLeptonDefinition.lepton_e_1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve1);
                    newStuff.putReplace(alpha);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "B+P": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p2)){
                    withThis.putUnify(dHadronDefinition.hadron_n1);
                    newStuff.putReplace(eLeptonDefinition.lepton_e_1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve1);
                    newStuff.putReplace(dHadronDefinition.hadron_p1);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "B+2P": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p3)){
                    withThis.putUnify(dHadronDefinition.hadron_n1);
                    newStuff.putReplace(eLeptonDefinition.lepton_e_1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve1);
                    newStuff.putReplace(dHadronDefinition.hadron_p2);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "B-A": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_n3, dHadronDefinition.hadron_p1)){
                    newStuff.putReplace(eLeptonDefinition.lepton_e1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve_1);
                    newStuff.putReplace(alpha);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "B-N": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_n2)){
                    withThis.putUnify(dHadronDefinition.hadron_p1);
                    newStuff.putReplace(eLeptonDefinition.lepton_e1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve_1);
                    newStuff.putReplace(dHadronDefinition.hadron_n1);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "B-2N": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_n3)){
                    withThis.putUnify(dHadronDefinition.hadron_p1);
                    newStuff.putReplace(eLeptonDefinition.lepton_e1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve_1);
                    newStuff.putReplace(dHadronDefinition.hadron_n2);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "B-P": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_n1)){
                    newStuff.putReplace(eLeptonDefinition.lepton_e1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve_1);
                    newStuff.putReplace(dHadronDefinition.hadron_p1);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "ECA": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_n1,eLeptonDefinition.lepton_e1,dHadronDefinition.hadron_p3)){
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve1);
                    newStuff.putReplace(alpha);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "ECP": {
                if (withThis.removeAllAmounts(false, eLeptonDefinition.lepton_e1,dHadronDefinition.hadron_p2)){
                    withThis.putUnify(dHadronDefinition.hadron_n1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve1);
                    newStuff.putReplace(dHadronDefinition.hadron_p1);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "EC2P": {
                if (withThis.removeAllAmounts(false, eLeptonDefinition.lepton_e1,dHadronDefinition.hadron_p3)){
                    withThis.putUnify(dHadronDefinition.hadron_n1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve1);
                    newStuff.putReplace(dHadronDefinition.hadron_p2);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "ECP+EC2P": {//todo look at branching ratios
                if (withThis.removeAllAmounts(false, eLeptonDefinition.lepton_e2,dHadronDefinition.hadron_p5)){
                    withThis.putUnify(dHadronDefinition.hadron_n1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve2);
                    newStuff.putReplace(dHadronDefinition.hadron_p3);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "N": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_n1)){
                    newStuff.putReplace(dHadronDefinition.hadron_n1);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "2N": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_n2)){
                    newStuff.putReplace(dHadronDefinition.hadron_n2);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "P": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p1)){
                    newStuff.putReplace(dHadronDefinition.hadron_p1);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "2P": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p2)){
                    newStuff.putReplace(dHadronDefinition.hadron_p2);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "SF": {
                if (Fission(decaysList, withThis, newStuff, decay.chance, false)) {
                    return true;
                }
            } break;
            case "B-F": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_n1)){
                    withThis.putUnify(dHadronDefinition.hadron_p1);
                    newStuff.putReplace(eLeptonDefinition.lepton_e1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve_1);
                    try{
                        if(Fission(decaysList,withThis,newStuff,decay.chance,false)) {
                            return true;
                        }
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "ECF": case "ECSF": case "EC(+SF)": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p1,eLeptonDefinition.lepton_e1)){
                    withThis.putUnify(dHadronDefinition.hadron_n1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve1);
                    try{
                        if(Fission(decaysList,withThis,newStuff,decay.chance,false)) {
                            return true;
                        }
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "SF(+EC+B+)": case "SF+EC+B+": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p2,eLeptonDefinition.lepton_e1)){
                    withThis.putUnify(dHadronDefinition.hadron_n2);
                    newStuff.putReplace(eLeptonDefinition.lepton_e_1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve2);
                    try{
                        if(Fission(decaysList,withThis,newStuff,decay.chance,false)) {
                            return true;
                        }
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "SF+EC+B-": {
                if (withThis.removeAllAmounts(false, eLeptonDefinition.lepton_e1)){
                    newStuff.putReplace(eLeptonDefinition.lepton_e1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve_1);
                    try{
                        if(Fission(decaysList,withThis,newStuff,decay.chance,false)) {
                            return true;
                        }
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "IT": case "IT?": case "G": {
                if(energy>0){
                    decaysList.add(new cElementalDecay(decay.chance, this, boson_Y__));
                }else{
                    if(DEBUG_MODE) {
                        TecTech.LOGGER.info("Tried to emit Gamma from ground state");
                    }
                    decaysList.add(new cElementalDecay(decay.chance, this));
                }
                return true;
            } //break;
            case "IT+EC+B+": {
                if (withThis.removeAllAmounts(false, dHadronDefinition.hadron_p2,eLeptonDefinition.lepton_e1)){
                    withThis.putUnify(dHadronDefinition.hadron_n2);
                    newStuff.putReplace(eLeptonDefinition.lepton_e_1);
                    newStuff.putReplace(eNeutrinoDefinition.lepton_Ve2);
                    newStuff.putReplace(eBosonDefinition.boson_Y__1);
                    try{
                        newStuff.putReplace(new dAtomDefinition(withThis.toImmutable_optimized_unsafeLeavesExposedElementalTree()).getStackForm(1));
                        decaysList.add(new cElementalDecay(decay.chance,newStuff.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
                        return true;
                    }catch (Exception e){
                        if(DEBUG_MODE) {
                            e.printStackTrace();
                        }
                    }
                }
            } break;
            case "DEAD_END":
                decaysList.add(deadEnd);
                return true;
            default: throw new Error("Unsupported decay mode: " + decay.decayName + ' ' + neutralCount+ ' ' +element);
        }
        if(DEBUG_MODE) {
            TecTech.LOGGER.info("Failed to decay " + element + ' ' + neutralCount + ' ' + decay.decayName);
        }
        return false;
    }

    private boolean Emmision(ArrayList<cElementalDecay> decaysList, cElementalDefinitionStack emit) {
        cElementalMutableDefinitionStackMap tree = elementalStacks.toMutable();
        if (tree.removeAmount(false, emit)) {
            try {
                decaysList.add(new cElementalDecay(1, new cElementalDefinitionStack(new dAtomDefinition(tree.toImmutable_optimized_unsafeLeavesExposedElementalTree()), 1), emit));
                return true;
            } catch (Exception e) {
                if (DEBUG_MODE) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    private boolean alphaDecay(ArrayList<cElementalDecay> decaysList) {
        cElementalMutableDefinitionStackMap tree = elementalStacks.toMutable();
        if (tree.removeAllAmounts(false, alpha.definition.getSubParticles())) {
            try {
                decaysList.add(new cElementalDecay(1, new cElementalDefinitionStack(new dAtomDefinition(tree.toImmutable_optimized_unsafeLeavesExposedElementalTree()), 1), alpha));
                return true;
            } catch (Exception e) {
                if (DEBUG_MODE) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    private boolean MbetaDecay(ArrayList<cElementalDecay> decaysList) {
        cElementalMutableDefinitionStackMap tree = elementalStacks.toMutable();
        if (tree.removeAmount(false, dHadronDefinition.hadron_n1)) {
            try {
                tree.putUnify(dHadronDefinition.hadron_p1);
                decaysList.add(new cElementalDecay(1, new cElementalDefinitionStack(new dAtomDefinition(tree.toImmutable_optimized_unsafeLeavesExposedElementalTree()), 1), eLeptonDefinition.lepton_e1, eNeutrinoDefinition.lepton_Ve_1));
                return true;
            } catch (Exception e) {
                if (DEBUG_MODE) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    private boolean PbetaDecay(ArrayList<cElementalDecay> decaysList) {
        cElementalMutableDefinitionStackMap tree = elementalStacks.toMutable();
        if (tree.removeAmount(false, dHadronDefinition.hadron_p1)) {
            try {
                tree.putUnify(dHadronDefinition.hadron_n1);
                decaysList.add(new cElementalDecay(1, new cElementalDefinitionStack(new dAtomDefinition(tree.toImmutable_optimized_unsafeLeavesExposedElementalTree()), 1), eLeptonDefinition.lepton_e_1, eNeutrinoDefinition.lepton_Ve1));
                return true;
            } catch (Exception e) {
                if (DEBUG_MODE) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    private boolean ElectronCapture(ArrayList<cElementalDecay> decaysList) {
        cElementalMutableDefinitionStackMap tree = elementalStacks.toMutable();
        if (tree.removeAllAmounts(false, dHadronDefinition.hadron_p1,eLeptonDefinition.lepton_e1)) {
            try {
                tree.putUnify(dHadronDefinition.hadron_n1);
                decaysList.add(new cElementalDecay(1, new cElementalDefinitionStack(new dAtomDefinition(tree.toImmutable_optimized_unsafeLeavesExposedElementalTree()), 1), eNeutrinoDefinition.lepton_Ve1));
                return true;
            } catch (Exception e) {
                if (DEBUG_MODE) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    private boolean Fission(ArrayList<cElementalDecay> decaysList, cElementalMutableDefinitionStackMap fissile, cElementalMutableDefinitionStackMap particles,double probability,boolean spontaneousCheck) {
        cElementalMutableDefinitionStackMap heavy = new cElementalMutableDefinitionStackMap();
        double[] liquidDrop= liquidDropFunction(Math.abs(element)<=97);

        for(cElementalDefinitionStack stack: fissile.values()){
            if(spontaneousCheck && stack.definition instanceof dHadronDefinition &&
                    (stack.amount<=80 || stack.amount<90 && XSTR_INSTANCE.nextInt(10)<stack.amount-80)) {
                return false;
            }
            if(stack.definition.getCharge()==0){
                //if(stack.definition instanceof dHadronDefinition){
                    double neutrals=stack.amount*liquidDrop[2];
                    int neutrals_cnt=(int)Math.floor(neutrals);
                    neutrals_cnt+=neutrals-neutrals_cnt>XSTR_INSTANCE.nextDouble()?1:0;
                    particles.putUnify(new cElementalDefinitionStack(stack.definition, neutrals_cnt));

                    int heavy_cnt=(int)Math.ceil(stack.amount*liquidDrop[1]);
                    while(heavy_cnt+neutrals_cnt>stack.amount) {
                        heavy_cnt--;
                    }
                    fissile.removeAmount(false,new cElementalDefinitionStack(stack.definition,heavy_cnt+neutrals_cnt));
                    heavy.putReplace(new cElementalDefinitionStack(stack.definition, heavy_cnt));
                //}else{
                //    particles.add(stack);
                //    light.remove(stack.definition);
                //}
            }else{
                int heavy_cnt=(int)Math.ceil(stack.amount*liquidDrop[0]);
                if(heavy_cnt%2==1 && XSTR_INSTANCE.nextDouble()>0.05D) {
                    heavy_cnt--;
                }
                cElementalDefinitionStack new_stack=new cElementalDefinitionStack(stack.definition, heavy_cnt);
                fissile.removeAmount(false,new_stack);
                heavy.putReplace(new_stack);
            }
        }

        try {
            particles.putReplace(new cElementalDefinitionStack(new dAtomDefinition(fissile.toImmutable_optimized_unsafeLeavesExposedElementalTree()),1));
            particles.putReplace(new cElementalDefinitionStack(new dAtomDefinition(heavy.toImmutable_optimized_unsafeLeavesExposedElementalTree()),1));
            decaysList.add(new cElementalDecay(probability, particles.toImmutable_optimized_unsafeLeavesExposedElementalTree()));
            return true;
        } catch (Exception e) {
            if(DEBUG_MODE) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static double[] liquidDropFunction(boolean asymmetric) {
        double[] out = new double[3];

        out[0] = XSTR_INSTANCE.nextGaussian();

        if (out[0] < 1 && out[0] >= -1) {
            if (XSTR_INSTANCE.nextBoolean()) {
                out[0] = XSTR_INSTANCE.nextDouble() * 2d - 1d;
            }
        }

        if (asymmetric && out[0] > XSTR_INSTANCE.nextDouble() && XSTR_INSTANCE.nextInt(4) == 0) {
            out[0] = -out[0];
        }

        //scale to splitting ratio
        out[0] = out[0] * 0.05d + .6d;

        if (out[0] < 0 || out[0] > 1) {
            return liquidDropFunction(asymmetric);
        }
        if (out[0] < .5d) {
            out[0] = 1d - out[0];
        }

        //extra neutrals
        out[2] = 0.012d + XSTR_INSTANCE.nextDouble() * 0.01d;

        if (asymmetric) {
            out[1] = out[0];
        } else {
            out[1] = out[0] - out[2] * .5d;
        }

        return out;
    }

    @Override
    public cElementalDecay[] getEnergyInducedDecay(long energyLevel) {
        if (iaeaDefinitionExistsAndHasEnergyLevels) {
            ArrayList<cElementalDecay> decays=new ArrayList<>(4);
            if(iaeaDecay(decays,energyLevel)){
                return decays.toArray(cElementalDecay.noProduct);
            }
        }
        if(energyLevel< Math.abs(charge)/3+neutralCount) {
            return new cElementalDecay[]{new cElementalDecay(1, this, boson_Y__)};
        }
        return getNaturalDecayInstant();
    }

    @Override
    public double getEnergyDiffBetweenStates(long currentEnergyLevel,long newEnergyLevel) {
        if(iaeaDefinitionExistsAndHasEnergyLevels){
            double result=0;
            boolean backwards=newEnergyLevel<currentEnergyLevel;
            if(backwards){
                long temp=currentEnergyLevel;
                currentEnergyLevel=newEnergyLevel;
                newEnergyLevel=temp;
            }

            if(currentEnergyLevel<=0){
                if(newEnergyLevel<=0) {
                    return iElementalDefinition.DEFAULT_ENERGY_REQUIREMENT * (newEnergyLevel - currentEnergyLevel);
                } else {
                    result += iElementalDefinition.DEFAULT_ENERGY_REQUIREMENT * -currentEnergyLevel;
                }
            }else {
                result -= iaea.energeticStatesArray[(int) Math.min(iaea.energeticStatesArray.length - 1, currentEnergyLevel)].energy;
            }
            if(newEnergyLevel>=iaea.energeticStatesArray.length){
                if(currentEnergyLevel>=iaea.energeticStatesArray.length) {
                    return iElementalDefinition.DEFAULT_ENERGY_REQUIREMENT * (newEnergyLevel - currentEnergyLevel);
                } else {
                    result += iElementalDefinition.DEFAULT_ENERGY_REQUIREMENT * (newEnergyLevel - iaea.energeticStatesArray.length + 1);
                }
                result+=iaea.energeticStatesArray[iaea.energeticStatesArray.length-1].energy;
            }else {
                result += iaea.energeticStatesArray[(int) Math.max(0, newEnergyLevel)].energy;
            }

            return backwards?-result:result;
        }
        return iElementalDefinition.DEFAULT_ENERGY_REQUIREMENT *(newEnergyLevel-currentEnergyLevel);
    }

    @Override
    public boolean usesSpecialEnergeticDecayHandling() {
        return iaeaDefinitionExistsAndHasEnergyLevels;
    }

    @Override
    public boolean usesMultipleDecayCalls(long energyLevel) {
        if(!iaeaDefinitionExistsAndHasEnergyLevels) return false;
        iaeaNuclide.energeticState state;
        if(energyLevel>iaea.energeticStatesArray.length) {
            state = iaea.energeticStatesArray[iaea.energeticStatesArray.length - 1];
        } else if(energyLevel<=0) {
            state = iaea.energeticStatesArray[0];
        } else {
            state = iaea.energeticStatesArray[(int) energyLevel];
        }
        for (iaeaNuclide.iaeaDecay decay:state.decaymodes){
            if(decay.decayName.contains("F")) return true;//if is fissile
        }
        return false;
    }

    @Override
    public boolean decayMakesEnergy(long energyLevel) {
        return iaeaDefinitionExistsAndHasEnergyLevels;
    }

    @Override
    public boolean fusionMakesEnergy(long energyLevel) {
        return iaea!=null || iaeaDefinitionExistsAndHasEnergyLevels;
    }

    @Override
    public cElementalDecay[] getNaturalDecayInstant() {
        //disembody
        ArrayList<cElementalDefinitionStack> decaysInto = new ArrayList<>();
        for (cElementalDefinitionStack elementalStack : elementalStacks.values()) {
            if (elementalStack.definition.getType() == 1 || elementalStack.definition.getType() == -1) {
                //covers both quarks and antiquarks
                decaysInto.add(elementalStack);
            } else {
                //covers both quarks and antiquarks
                decaysInto.add(new cElementalDefinitionStack(boson_Y__, 2));
            }
        }
        return new cElementalDecay[]{new cElementalDecay(0.75D, decaysInto.toArray(new cElementalDefinitionStack[0])), deadEnd};
    }

    //@Override
    //public iElementalDefinition getAnti() {
    //    cElementalDefinitionStack[] stacks = this.elementalStacks.values();
    //    cElementalDefinitionStack[] antiElements = new cElementalDefinitionStack[stacks.length];
    //    for (int i = 0; i < antiElements.length; i++) {
    //        antiElements[i] = new cElementalDefinitionStack(stacks[i].definition.getAnti(), stacks[i].amount);
    //    }
    //    try {
    //        return new dAtomDefinition(false, antiElements);
    //    } catch (tElementalException e) {
    //        if (DEBUG_MODE) e.printStackTrace();
    //        return null;
    //    }
    //}

    @Override
    public dAtomDefinition getAnti() {
        cElementalMutableDefinitionStackMap anti = new cElementalMutableDefinitionStackMap();
        for (cElementalDefinitionStack stack : elementalStacks.values()) {
            anti.putReplace(new cElementalDefinitionStack(stack.definition.getAnti(), stack.amount));
        }
        try {
            return new dAtomDefinition(anti.toImmutable_optimized_unsafeLeavesExposedElementalTree());
        } catch (tElementalException e) {
            if (DEBUG_MODE) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Override
    public aFluidDequantizationInfo someAmountIntoFluidStack() {
        HashMap<iElementalDefinition, aFluidDequantizationInfo> temp = TRANSFORMATION_INFO.fluidDequantization;
        if (temp == null)
            return null;
        return temp.get(this);
    }

    @Override
    public aItemDequantizationInfo someAmountIntoItemsStack() {
        HashMap<iElementalDefinition, aItemDequantizationInfo> temp = TRANSFORMATION_INFO.itemDequantization;
        if (temp == null)
            return null;
        return temp.get(this);
    }

    @Override
    public aOredictDequantizationInfo someAmountIntoOredictStack() {
        HashMap<iElementalDefinition, aOredictDequantizationInfo> temp = TRANSFORMATION_INFO.oredictDequantization;
        if (temp == null)
            return null;
        return temp.get(this);
    }

    private static final class Nomenclature {
        private static final String[] SYMBOL = new String[]{"Nt", "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne", "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar", "K", "Ca", "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn", "Ga", "Ge", "As", "Se", "Br", "Kr", "Rb", "Sr", "Y", "Zr", "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn", "Sb", "Te", "I", "Xe", "Cs", "Ba", "La", "Ce", "Pr", "Nd", "Pm", "Sm", "Eu", "Gd", "Tb", "Dy", "Ho", "Er", "Tm", "Yb", "Lu", "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg", "Tl", "Pb", "Bi", "Po", "At", "Rn", "Fr", "Ra", "Ac", "Th", "Pa", "U", "Np", "Pu", "Am", "Cm", "Bk", "Cf", "Es", "Fm", "Md", "No", "Lr", "Rf", "Db", "Sg", "Bh", "Hs", "Mt", "Ds", "Rg", "Cn", "Nh", "Fl", "Mc", "Lv", "Ts", "Og"};
        private static final String[] NAME = new String[]{"Neutronium", "Hydrogen", "Helium", "Lithium", "Beryllium", "Boron", "Carbon", "Nitrogen", "Oxygen", "Fluorine", "Neon", "Sodium", "Magnesium", "Aluminium", "Silicon", "Phosphorus", "Sulfur", "Chlorine", "Argon", "Potassium", "Calcium", "Scandium", "Titanium", "Vanadium", "Chromium", "Manganese", "Iron", "Cobalt", "Nickel", "Copper", "Zinc", "Gallium", "Germanium", "Arsenic", "Selenium", "Bromine", "Krypton", "Rubidium", "Strontium", "Yttrium", "Zirconium", "Niobium", "Molybdenum", "Technetium", "Ruthenium", "Rhodium", "Palladium", "Silver", "Cadmium", "Indium", "Tin", "Antimony", "Tellurium", "Iodine", "Xenon", "Caesium", "Barium", "Lanthanum", "Cerium", "Praseodymium", "Neodymium", "Promethium", "Samarium", "Europium", "Gadolinium", "Terbium", "Dysprosium", "Holmium", "Erbium", "Thulium", "Ytterbium", "Lutetium", "Hafnium", "Tantalum", "Tungsten", "Rhenium", "Osmium", "Iridium", "Platinum", "Gold", "Mercury", "Thallium", "Lead", "Bismuth", "Polonium", "Astatine", "Radon", "Francium", "Radium", "Actinium", "Thorium", "Protactinium", "Uranium", "Neptunium", "Plutonium", "Americium", "Curium", "Berkelium", "Californium", "Einsteinium", "Fermium", "Mendelevium", "Nobelium", "Lawrencium", "Rutherfordium", "Dubnium", "Seaborgium", "Bohrium", "Hassium", "Meitnerium", "Darmstadtium", "Roentgenium", "Copernicium", "Nihonium", "Flerovium", "Moscovium", "Livermorium", "Tennessine", "Oganesson"};
        private static final String[] SYMBOL_IUPAC = new String[]{"n", "u", "b", "t", "q", "p", "h", "s", "o", "e", "N", "U", "B", "T", "Q", "P", "H", "S", "O", "E"};
    }

    @Override
    public NBTTagCompound toNBT() {
        return getNbtTagCompound(nbtType, elementalStacks);
    }

    public static dAtomDefinition fromNBT(NBTTagCompound nbt) {
        cElementalDefinitionStack[] stacks = new cElementalDefinitionStack[nbt.getInteger("i")];
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = cElementalDefinitionStack.fromNBT(nbt.getCompoundTag(Integer.toString(i)));
        }
        try {
            return new dAtomDefinition(stacks);
        } catch (tElementalException e) {
            if (DEBUG_MODE) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static void run() {
        for (Runnable r : overrides) {
            r.run();
        }

        for(Map.Entry<dAtomDefinition,Double> entry:lifetimeOverrides.entrySet()){
            try {
                lifetimeOverrides.put(new dAtomDefinition(entry.getKey().elementalStacks), entry.getValue());
            }catch (tElementalException e){
                e.printStackTrace(); //Impossible
            }
        }

        //populate stable isotopes
        for (int element = 1; element < 83; element++)//Up to Bismuth exclusive
        {
            for (int isotope = 0; isotope < 130; isotope++) {
                xstr.setSeed((long) (element + 1) * (isotope + 100));
                //stability curve
                int StableIsotope = stableIzoCurve(element);
                int izoDiff = isotope - StableIsotope;
                int izoDiffAbs = Math.abs(izoDiff);
                double rawLifeTime = calculateLifeTime(izoDiff, izoDiffAbs, element, isotope, false);
                iaeaNuclide nuclide = iaeaNuclide.get(element, isotope);
                if (rawLifeTime >= STABLE_RAW_LIFE_TIME || nuclide != null && nuclide.halfTime >= STABLE_RAW_LIFE_TIME) {
                    TreeSet<Integer> isotopes = stableIsotopes.computeIfAbsent(element, k -> new TreeSet<>());
                    isotopes.add(isotope);
                }
            }
        }

        //populate unstable isotopes
        for (int element = 83; element < 150; element++) {
            for (int isotope = 100; isotope < 180; isotope++) {
                xstr.setSeed((long) (element + 1) * (isotope + 100));
                //stability curve
                int Isotope = stableIzoCurve(element);
                int izoDiff = isotope - Isotope;
                int izoDiffAbs = Math.abs(izoDiff);
                double rawLifeTime = calculateLifeTime(izoDiff, izoDiffAbs, element, isotope, false);
                TreeMap<Double, Integer> isotopes = mostStableUnstableIsotopes.computeIfAbsent(element, k -> new TreeMap<>());
                isotopes.put(rawLifeTime, isotope);
            }
        }

        try {
            for (Map.Entry<Integer, TreeSet<Integer>> integerTreeSetEntry : stableIsotopes.entrySet()) {
                stableAtoms.put(integerTreeSetEntry.getKey(), new dAtomDefinition(
                        new cElementalDefinitionStack(dHadronDefinition.hadron_p, integerTreeSetEntry.getKey()),
                        new cElementalDefinitionStack(dHadronDefinition.hadron_n, integerTreeSetEntry.getValue().first()),
                        new cElementalDefinitionStack(eLeptonDefinition.lepton_e, integerTreeSetEntry.getKey())));
                if (DEBUG_MODE) {
                    TecTech.LOGGER.info("Added Stable Atom:" + integerTreeSetEntry.getKey() + ' ' + integerTreeSetEntry.getValue().first() + ' ' + stableAtoms.get(integerTreeSetEntry.getKey()).getMass());
                }
            }
            for (Map.Entry<Integer, TreeMap<Double, Integer>> integerTreeMapEntry : mostStableUnstableIsotopes.entrySet()) {
                unstableAtoms.put(integerTreeMapEntry.getKey(), new dAtomDefinition(
                        new cElementalDefinitionStack(dHadronDefinition.hadron_p, integerTreeMapEntry.getKey()),
                        new cElementalDefinitionStack(dHadronDefinition.hadron_n, integerTreeMapEntry.getValue().lastEntry().getValue()),
                        new cElementalDefinitionStack(eLeptonDefinition.lepton_e, integerTreeMapEntry.getKey())));
                if (DEBUG_MODE) {
                    TecTech.LOGGER.info("Added Unstable Atom:" + integerTreeMapEntry.getKey() + ' ' + integerTreeMapEntry.getValue().lastEntry().getValue() + ' ' + unstableAtoms.get(integerTreeMapEntry.getKey()).getMass());
                }
            }
            deuterium=new dAtomDefinition(
                    dHadronDefinition.hadron_p1,
                    dHadronDefinition.hadron_n1,
                    eLeptonDefinition.lepton_e1).getStackForm(1);
            tritium=new dAtomDefinition(
                    dHadronDefinition.hadron_p1,
                    dHadronDefinition.hadron_n2,
                    eLeptonDefinition.lepton_e1).getStackForm(1);
            helium_3=new dAtomDefinition(
                    dHadronDefinition.hadron_p2,
                    dHadronDefinition.hadron_n1,
                    eLeptonDefinition.lepton_e2).getStackForm(1);
            alpha = new dAtomDefinition(
                    dHadronDefinition.hadron_p2,
                    dHadronDefinition.hadron_n2).getStackForm(1);
            beryllium_8=new dAtomDefinition(
                    new cElementalDefinitionStack(dHadronDefinition.hadron_p, 4),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_n, 4),
                    new cElementalDefinitionStack(eLeptonDefinition.lepton_e, 4)).getStackForm(1);
            carbon_14=new dAtomDefinition(
                    new cElementalDefinitionStack(dHadronDefinition.hadron_p, 6),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_n, 8),
                    new cElementalDefinitionStack(eLeptonDefinition.lepton_e, 6)).getStackForm(1);
            neon_24=new dAtomDefinition(
                    new cElementalDefinitionStack(dHadronDefinition.hadron_p, 10),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_n, 14),
                    new cElementalDefinitionStack(eLeptonDefinition.lepton_e, 10)).getStackForm(1);
            silicon_34=new dAtomDefinition(
                    new cElementalDefinitionStack(dHadronDefinition.hadron_p, 14),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_n, 20),
                    new cElementalDefinitionStack(eLeptonDefinition.lepton_e, 14)).getStackForm(1);
        } catch (Exception e) {
            if (DEBUG_MODE) {
                e.printStackTrace();
            }
        }

        try {
            cElementalDefinition.addCreatorFromNBT(nbtType, dAtomDefinition.class.getMethod("fromNBT", NBTTagCompound.class),(byte)64);
        } catch (Exception e) {
            if (DEBUG_MODE) {
                e.printStackTrace();
            }
        }
        if(DEBUG_MODE) {
            TecTech.LOGGER.info("Registered Elemental Matter Class: Atom " + nbtType + ' ' + 64);
        }
    }

    public static void setTransformation(){
        /*----STABLE ATOMS----**/
        refMass = getFirstStableIsotope(1).getMass() * AVOGADRO_CONSTANT_144;

        TRANSFORMATION_INFO.addFluid(new cElementalDefinitionStack(getFirstStableIsotope(1), AVOGADRO_CONSTANT_144),Materials.Hydrogen.mGas,144);
        TRANSFORMATION_INFO.addFluid(new cElementalDefinitionStack(getFirstStableIsotope(2), AVOGADRO_CONSTANT_144),Materials.Helium.mGas, 144);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(3), AVOGADRO_CONSTANT_144), dust, Materials.Lithium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(4), AVOGADRO_CONSTANT_144), dust, Materials.Beryllium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(5), AVOGADRO_CONSTANT_144), dust, Materials.Boron,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(6), AVOGADRO_CONSTANT_144), dust, Materials.Carbon,1);
        TRANSFORMATION_INFO.addFluid(new cElementalDefinitionStack(getFirstStableIsotope(7), AVOGADRO_CONSTANT_144),Materials.Nitrogen.mGas, 144);
        TRANSFORMATION_INFO.addFluid(new cElementalDefinitionStack(getFirstStableIsotope(8), AVOGADRO_CONSTANT_144),Materials.Oxygen.mGas, 144);
        TRANSFORMATION_INFO.addFluid(new cElementalDefinitionStack(getFirstStableIsotope(9), AVOGADRO_CONSTANT_144),Materials.Fluorine.mGas, 144);
        //transformation.addFluid(new cElementalDefinitionStack(getFirstStableIsotope(10), AVOGADRO_CONSTANT_144),Materials.Neon.mGas.getID(), 144);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(11), AVOGADRO_CONSTANT_144), dust, Materials.Sodium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(12), AVOGADRO_CONSTANT_144), dust, Materials.Magnesium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(13), AVOGADRO_CONSTANT_144), dust, Materials.Aluminium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(14), AVOGADRO_CONSTANT_144), dust, Materials.Silicon,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(15), AVOGADRO_CONSTANT_144), dust, Materials.Phosphorus,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(16), AVOGADRO_CONSTANT_144), dust, Materials.Sulfur,1);
        TRANSFORMATION_INFO.addFluid(new cElementalDefinitionStack(getFirstStableIsotope(17), AVOGADRO_CONSTANT_144),Materials.Argon.mGas, 144);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(19), AVOGADRO_CONSTANT_144), dust, Materials.Potassium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(20), AVOGADRO_CONSTANT_144), dust, Materials.Calcium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(21), AVOGADRO_CONSTANT_144), dust, Materials.Scandium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(22), AVOGADRO_CONSTANT_144), dust, Materials.Titanium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(23), AVOGADRO_CONSTANT_144), dust, Materials.Vanadium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(24), AVOGADRO_CONSTANT_144), dust, Materials.Chrome,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(25), AVOGADRO_CONSTANT_144), dust, Materials.Manganese,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(26), AVOGADRO_CONSTANT_144), dust, Materials.Iron,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(27), AVOGADRO_CONSTANT_144), dust, Materials.Cobalt,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(28), AVOGADRO_CONSTANT_144), dust, Materials.Nickel,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(29), AVOGADRO_CONSTANT_144), dust, Materials.Copper,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(30), AVOGADRO_CONSTANT_144), dust, Materials.Zinc,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(31), AVOGADRO_CONSTANT_144), dust, Materials.Gallium,1);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(32), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Germanium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(33), AVOGADRO_CONSTANT_144), dust, Materials.Arsenic,1);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(34), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Selenium,1);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(35), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Bromine,1);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(36), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Krypton,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(37), AVOGADRO_CONSTANT_144), dust, Materials.Rubidium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(38), AVOGADRO_CONSTANT_144), dust, Materials.Strontium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(39), AVOGADRO_CONSTANT_144), dust, Materials.Yttrium,1);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(40), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Zirconium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(41), AVOGADRO_CONSTANT_144), dust, Materials.Niobium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(42), AVOGADRO_CONSTANT_144), dust, Materials.Molybdenum,1);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(43), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Technetium,1);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(44), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Ruthenium,1);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(45), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Rhodium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(46), AVOGADRO_CONSTANT_144), dust, Materials.Palladium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(47), AVOGADRO_CONSTANT_144), dust, Materials.Silver,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(48), AVOGADRO_CONSTANT_144), dust, Materials.Cadmium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(49), AVOGADRO_CONSTANT_144), dust, Materials.Indium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(50), AVOGADRO_CONSTANT_144), dust, Materials.Tin,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(51), AVOGADRO_CONSTANT_144), dust, Materials.Antimony,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(52), AVOGADRO_CONSTANT_144), dust, Materials.Tellurium,1);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(53), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Iodine,1);
        //transformation.addFluid(new cElementalDefinitionStack(getFirstStableIsotope(54), AVOGADRO_CONSTANT_144),Materials.Xenon.mGas.getID(), 144);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(55), AVOGADRO_CONSTANT_144), dust, Materials.Caesium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(56), AVOGADRO_CONSTANT_144), dust, Materials.Barium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(57), AVOGADRO_CONSTANT_144), dust, Materials.Lanthanum,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(58), AVOGADRO_CONSTANT_144), dust, Materials.Cerium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(59), AVOGADRO_CONSTANT_144), dust, Materials.Praseodymium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(60), AVOGADRO_CONSTANT_144), dust, Materials.Neodymium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(61), AVOGADRO_CONSTANT_144), dust, Materials.Promethium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(62), AVOGADRO_CONSTANT_144), dust, Materials.Samarium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(63), AVOGADRO_CONSTANT_144), dust, Materials.Europium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(64), AVOGADRO_CONSTANT_144), dust, Materials.Gadolinium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(65), AVOGADRO_CONSTANT_144), dust, Materials.Terbium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(66), AVOGADRO_CONSTANT_144), dust, Materials.Dysprosium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(67), AVOGADRO_CONSTANT_144), dust, Materials.Holmium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(68), AVOGADRO_CONSTANT_144), dust, Materials.Erbium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(69), AVOGADRO_CONSTANT_144), dust, Materials.Thulium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(70), AVOGADRO_CONSTANT_144), dust, Materials.Ytterbium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(71), AVOGADRO_CONSTANT_144), dust, Materials.Lutetium,1);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(72), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Hafnum,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(73), AVOGADRO_CONSTANT_144), dust, Materials.Tantalum,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(74), AVOGADRO_CONSTANT_144), dust, Materials.Tungsten,1);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(75), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Rhenium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(76), AVOGADRO_CONSTANT_144), dust, Materials.Osmium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(77), AVOGADRO_CONSTANT_144), dust, Materials.Iridium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(78), AVOGADRO_CONSTANT_144), dust, Materials.Platinum,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(79), AVOGADRO_CONSTANT_144), dust, Materials.Gold,1);
        TRANSFORMATION_INFO.addFluid(new cElementalDefinitionStack(getFirstStableIsotope(80), AVOGADRO_CONSTANT_144),Materials.Mercury.mFluid, 144);
        //transformation.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(81), AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Thallium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getFirstStableIsotope(82), AVOGADRO_CONSTANT_144), dust, Materials.Lead,1);

        /*----UNSTABLE ATOMS----**/
        refUnstableMass = getFirstStableIsotope(82).getMass() * AVOGADRO_CONSTANT_144;

        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getBestUnstableIsotope(83), AVOGADRO_CONSTANT_144), dust, Materials.Bismuth,1);
        //transformation.addOredict(new cElementalDefinitionStack(getBestUnstableIsotope(84),AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Polonium,1);
        //transformation.addFluid(new cElementalDefinitionStack(getBestUnstableIsotope(85),AVOGADRO_CONSTANT_144),Materials.Astatine.mPlasma.getID(), 144);
        TRANSFORMATION_INFO.addFluid(new cElementalDefinitionStack(getBestUnstableIsotope(86), AVOGADRO_CONSTANT_144),Materials.Radon.mGas, 144);
        //transformation.addOredict(new cElementalDefinitionStack(getBestUnstableIsotope(87),AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Francium,1);
        //transformation.addOredict(new cElementalDefinitionStack(getBestUnstableIsotope(88),AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Radium,1);
        //transformation.addOredict(new cElementalDefinitionStack(getBestUnstableIsotope(89),AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Actinium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getBestUnstableIsotope(90), AVOGADRO_CONSTANT_144), dust, Materials.Thorium,1);
        //transformation.addOredict(new cElementalDefinitionStack(getBestUnstableIsotope(91),AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Protactinium,1);
        ////transformation.addOredict(new cElementalDefinitionStack(getBestUnstableIsotope(92),AVOGADRO_CONSTANT_144), dust, Materials.Uranium,1);
        //transformation.addOredict(new cElementalDefinitionStack(getBestUnstableIsotope(93),AVOGADRO_CONSTANT_144),OrePrefixes.dust, Materials.Neptunium,1);
        ////transformation.addOredict(new cElementalDefinitionStack(getBestUnstableIsotope(94),AVOGADRO_CONSTANT_144), dust, Materials.Plutonium,1);
        TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(getBestUnstableIsotope(95), AVOGADRO_CONSTANT_144), dust, Materials.Americium,1);

        try {
            dAtomDefinition temp;
            TRANSFORMATION_INFO.addFluid(new cElementalDefinitionStack(deuterium.definition, AVOGADRO_CONSTANT_144),Materials.Deuterium.mGas, 144);

            TRANSFORMATION_INFO.addFluid(new cElementalDefinitionStack(tritium.definition, AVOGADRO_CONSTANT_144),Materials.Tritium.mGas, 144);

            TRANSFORMATION_INFO.addFluid(new cElementalDefinitionStack(helium_3.definition, AVOGADRO_CONSTANT_144),Materials.Helium_3.mGas, 144);

            temp=new dAtomDefinition(
                    new cElementalDefinitionStack(eLeptonDefinition.lepton_e, 92),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_p, 92),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_n, 146)
            );
            TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(temp, AVOGADRO_CONSTANT_144), dust, Materials.Uranium/*238*/,1);

            double tempMass=temp.getMass();

            temp=new dAtomDefinition(
                    new cElementalDefinitionStack(eLeptonDefinition.lepton_e, 92),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_p, 92),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_n, 143)
            );
            TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(temp, AVOGADRO_CONSTANT_144), dust, Materials.Uranium235,1);

            TecTech.LOGGER.info("Diff Mass U : "+(tempMass-temp.getMass()));

            temp=new dAtomDefinition(
                    new cElementalDefinitionStack(eLeptonDefinition.lepton_e, 94),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_p, 94),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_n, 145)
            );
            TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(temp, AVOGADRO_CONSTANT_144), dust, Materials.Plutonium/*239*/,1);

            somethingHeavy=new dAtomDefinition(
                    new cElementalDefinitionStack(eLeptonDefinition.lepton_e, 94),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_p, 94),
                    new cElementalDefinitionStack(dHadronDefinition.hadron_n, 147)
            );
            TRANSFORMATION_INFO.addOredict(new cElementalDefinitionStack(somethingHeavy, AVOGADRO_CONSTANT_144), dust, Materials.Plutonium241,1);

            TecTech.LOGGER.info("Diff Mass Pu: "+(somethingHeavy.getMass()-temp.getMass()));

            TecTech.LOGGER.info("Neutron Mass: "+dHadronDefinition.hadron_n.getMass());

        } catch (tElementalException e) {
            if (DEBUG_MODE) {
                e.printStackTrace();
            }
        }

        if(Loader.isModLoaded(Reference.GTPLUSPLUS)) {
            new GtppAtomLoader().run();
        }
    }

    public static dAtomDefinition getFirstStableIsotope(int element) {
        return stableAtoms.get(element);
    }

    public static dAtomDefinition getBestUnstableIsotope(int element) {
        return unstableAtoms.get(element);
    }

    @Override
    public byte getClassType() {
        return 64;
    }

    public static byte getClassTypeStatic(){
        return 64;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public void addScanShortSymbols(ArrayList<String> lines, int capabilities, long energyLevel) {
        if(Util.areBitsSet(SCAN_GET_NOMENCLATURE|SCAN_GET_CHARGE|SCAN_GET_MASS|SCAN_GET_TIMESPAN_INFO, capabilities)) {
            lines.add(getShortSymbol());
        }
    }

    @Override
    public void addScanResults(ArrayList<String> lines, int capabilities, long energyLevel) {
        if(Util.areBitsSet(SCAN_GET_CLASS_TYPE, capabilities)) {
            lines.add("CLASS = " + nbtType + ' ' + getClassType());
        }
        if(Util.areBitsSet(SCAN_GET_NOMENCLATURE|SCAN_GET_CHARGE|SCAN_GET_MASS|SCAN_GET_TIMESPAN_INFO, capabilities)) {
            lines.add("NAME = "+getName());
            lines.add("SYMBOL = "+getSymbol());
        }
        if(Util.areBitsSet(SCAN_GET_CHARGE,capabilities)) {
            lines.add("CHARGE = " + getCharge() / 3D + " e");
        }
        if(Util.areBitsSet(SCAN_GET_COLOR,capabilities)) {
            lines.add(getColor() < 0 ? "COLORLESS" : "CARRIES COLOR");
        }
        if(Util.areBitsSet(SCAN_GET_MASS,capabilities)) {
            lines.add("MASS = " + getMass() + " eV/c\u00b2");
        }
        if(iaeaDefinitionExistsAndHasEnergyLevels && Util.areBitsSet(SCAN_GET_ENERGY_STATES,capabilities)){
            for(int i=1;i<iaea.energeticStatesArray.length;i++){
                lines.add("E LEVEL "+i+" = "+iaea.energeticStatesArray[i].energy+" eV");
            }
        }
        if(Util.areBitsSet(SCAN_GET_TIMESPAN_INFO, capabilities)){
            lines.add("HALF LIFE = "+getRawTimeSpan(energyLevel)+ " s");
            lines.add("    At current energy level");
        }
    }
}
