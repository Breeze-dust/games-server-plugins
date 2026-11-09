package com.superniuma.game;

import com.superniuma.mechanism.MechanismChoice;

import java.util.UUID;

/** 房间中的一名玩家。 */
public final class Participant {

    private final UUID uuid;
    private final String name;
    private int teamId;
    private double score;
    private boolean finished;
    private boolean failed;
    private int finishOrder;
    private long finishTime;
    private MechanismChoice choice;
    private String chosenOptionId;
    private boolean placed;
    private double roundCoinPoints;
    private int roundTrapFails;

    public Participant(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public int teamId() {
        return teamId;
    }

    public void setTeamId(int teamId) {
        this.teamId = teamId;
    }

    public double score() {
        return score;
    }

    public void addScore(double amount) {
        this.score += amount;
    }

    public boolean finished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public boolean failed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public int finishOrder() {
        return finishOrder;
    }

    public void setFinishOrder(int finishOrder) {
        this.finishOrder = finishOrder;
    }

    public long finishTime() {
        return finishTime;
    }

    public void setFinishTime(long finishTime) {
        this.finishTime = finishTime;
    }

    public MechanismChoice choice() {
        return choice;
    }

    public void setChoice(MechanismChoice choice) {
        this.choice = choice;
    }

    public String chosenOptionId() {
        return chosenOptionId;
    }

    public void setChosenOptionId(String chosenOptionId) {
        this.chosenOptionId = chosenOptionId;
    }

    public boolean placed() {
        return placed;
    }

    public void setPlaced(boolean placed) {
        this.placed = placed;
    }

    public double roundCoinPoints() {
        return roundCoinPoints;
    }

    public void addRoundCoinPoints(double amount) {
        this.roundCoinPoints += amount;
    }

    public int roundTrapFails() {
        return roundTrapFails;
    }

    public void addRoundTrapFails(int amount) {
        this.roundTrapFails += amount;
    }

    /** 每回合开始时清空本回合临时数据。 */
    public void resetRound() {
        finished = false;
        failed = false;
        finishOrder = 0;
        finishTime = 0L;
        choice = null;
        chosenOptionId = null;
        placed = false;
        roundCoinPoints = 0.0;
        roundTrapFails = 0;
    }
}
