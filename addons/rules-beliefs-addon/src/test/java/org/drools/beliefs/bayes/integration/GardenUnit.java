package org.drools.beliefs.bayes.integration;

import org.drools.beliefs.bayes.VarName;

public class GardenUnit {

    @VarName("WetGrass")
    private double[] wetGrassEvidence;
    @VarName("Cloudy")
    private double[] cloudyEvidence;
    @VarName("Sprinkler")
    private double[] sprinklerEvidence;
    @VarName("Rain")
    private double[] rainEvidence;

    public double[] getWetGrassEvidence() {
        return wetGrassEvidence;
    }

    public void setWetGrassEvidence(double... wetGrassEvidence) {
        this.wetGrassEvidence = wetGrassEvidence;
    }

    public double[] getCloudyEvidence() {
        return cloudyEvidence;
    }

    public void setCloudyEvidence(double... cloudyEvidence) {
        this.cloudyEvidence = cloudyEvidence;
    }

    public double[] getSprinklerEvidence() {
        return sprinklerEvidence;
    }

    public void setSprinklerEvidence(double... sprinklerEvidence) {
        this.sprinklerEvidence = sprinklerEvidence;
    }

    public double[] getRainEvidence() {
        return rainEvidence;
    }

    public void setRainEvidence(double... rainEvidence) {
        this.rainEvidence = rainEvidence;
    }
}
