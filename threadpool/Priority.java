package threadpool;

public enum Priority{
    HIGH(10),
    MEDIUM(5),
    LOW(1);

    private final int value;

    Priority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}