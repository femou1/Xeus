package com.pinewoodbuilders.contracts.roblox.evaluations;

public enum PassedEvals {
    QUIZ ("passed_quiz", "Quiz", "later", "later", "later"),
    PATROL ("passed_patrol", "Patrol", "later", "later", "later"),
    COMBAT ("passed_combat", "Combat", "later", "later", "later");

    public String sql_name;
    public String label;
    public String passedEmoji;
    public String failedEmoji;
    public String neutralEmoji;

    PassedEvals(String name, String label, String passedEmoji, String failedEmoji, String neutralEmoji) {
        this.sql_name = name;
        this.label = label;
        this.passedEmoji = passedEmoji;
        this.failedEmoji = failedEmoji;
        this.neutralEmoji = neutralEmoji;
    }

    public String getName() {
        return sql_name;
    }

    public String getLabel() {
        return label;
    }

    public String getPassedEmoji() {
        return passedEmoji;
    }

    public String getFailedEmoji() {
        return failedEmoji;
    }

    public String getNeutralEmoji() {
        return neutralEmoji;
    }
}
