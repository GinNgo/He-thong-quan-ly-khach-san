package com.hotel.dtos;

public class PermissionDTO {
    private String function;
    private int actionMask;

    public PermissionDTO(String function, int actionMask) {
        this.function = function;
        this.actionMask = actionMask;
    }

    public String getFunction() { return function; }
    public void setFunction(String function) { this.function = function; }
    public int getActionMask() { return actionMask; }
    public void setActionMask(int actionMask) { this.actionMask = actionMask; }
}
