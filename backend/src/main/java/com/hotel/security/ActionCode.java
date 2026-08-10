package com.hotel.security;

public class ActionCode {
    public static final int VIEW = 1;
    public static final int CREATE = 2;
    public static final int UPDATE = 4;
    public static final int DELETE = 8;
    public static final int EXPORT = 16;
    public static final int APPROVE = 32;
    public static final int TASK_EXECUTE = 64;

    public static final int ALL = VIEW | CREATE | UPDATE | DELETE | EXPORT | APPROVE | TASK_EXECUTE;

    private ActionCode() {
    }
}
