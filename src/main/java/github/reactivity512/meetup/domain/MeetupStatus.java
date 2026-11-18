package github.reactivity512.meetup.domain;

public enum MeetupStatus {

    QUEUED("В очереди", "secondary"),
    PROCESSING("Распознаётся", "info"),
    READY("Готов", "success"),
    FAILED("Ошибка", "danger");

    private final String label;
    private final String badgeClass;

    MeetupStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public boolean isBusy() {
        return this == QUEUED || this == PROCESSING;
    }

    public boolean isReady() {
        return this == READY;
    }

    public boolean isFailed() {
        return this == FAILED;
    }
}
