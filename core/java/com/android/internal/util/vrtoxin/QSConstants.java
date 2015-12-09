/*
 * Copyright (C) 2015 The CyanogenMod Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.internal.util.vrtoxin;

import java.util.ArrayList;

public class QSConstants {
    private QSConstants() {}

    public static final String TILE_WIFI = "wifi";
    public static final String TILE_BLUETOOTH = "bt";
    public static final String TILE_INVERSION = "inversion";
    public static final String TILE_CELLULAR = "cell";
    public static final String TILE_AIRPLANE = "airplane";
    public static final String TILE_ROTATION = "rotation";
    public static final String TILE_FLASHLIGHT = "flashlight";
    public static final String TILE_LOCATION = "location";
    public static final String TILE_CAST = "cast";
    public static final String TILE_HOTSPOT = "hotspot";
    public static final String TILE_NOTIFICATIONS = "notifications";
    public static final String TILE_DATA = "data";
    public static final String TILE_ROAMING = "roaming";
    public static final String TILE_DND = "dnd";
    public static final String TILE_APN = "apn";
    public static final String TILE_POWERMENU = "togglePowerMenu";
    public static final String TILE_ADB_NETWORK = "adb_network";
    public static final String TILE_NFC = "nfc";
    public static final String TILE_COMPASS = "compass";
    public static final String TILE_LOCKSCREEN = "lockscreen";
    public static final String TILE_LTE = "lte";
    public static final String TILE_VISUALIZER = "visualizer";
    public static final String TILE_VOLUME = "volume";
    public static final String TILE_SCREEN_TIMEOUT = "screen_timeout";
    public static final String TILE_VRTOXIN = "vrtoxin_settings";
    public static final String TILE_SCREENSHOT = "screenshot";
    public static final String TILE_SYNC = "sync";
    public static final String TILE_BRIGHTNESS = "brightness";
    public static final String TILE_BATTERY_SAVER = "battery_saver";
    public static final String TILE_SCREENOFF = "screen_off";
    public static final String TILE_EXPANDED_DESKTOP = "expanded_desktop";
    public static final String TILE_APPCIRCLEBAR = "toggleAppCircleBar";
    public static final String TILE_REBOOT = "reboot";
    public static final String TILE_AMBIENT_DISPLAY = "ambient_display";
    public static final String TILE_USB_TETHERING = "usb_tethering";
    public static final String TILE_SLIMACTION = "slimaction";
    public static final String TILE_GESTURE_ANYWHERE = "toggleGestureAnywhere";
    public static final String TILE_SLIMPIE = "toggleSlimPie";
    public static final String TILE_NAVBAR = "toggleNavBar";
    public static final String TILE_HWKEYS = "hwkeys";
    public static final String TILE_HEADSUP = "toggleHeadsUp";
    public static final String TILE_MUSIC = "music";
    public static final String TILE_TRDS = "trds";
    public static final String TILE_APPSIDEBAR = "toggleAppSideBar";
    public static final String TILE_PA_PIE_CONTROL = "togglePAPieControl";
    public static final String TILE_SYSTEMUI_RESTART = "reboot_systemui";
    public static final String TILE_FLOATING_WINDOWS = "floating_windows";
    public static final String TILE_SLIM_FLOATS = "slim_floats";
    public static final String TILE_KERNEL_ADIUTOR = "kernel_adiutor";
    public static final String TILE_STWEAKS = "stweaks";
    public static final String TILE_LED = "led";

    protected static final ArrayList<String> STATIC_TILES_AVAILABLE = new ArrayList<String>();
    protected static final ArrayList<String> TILES_AVAILABLE = new ArrayList<String>();

    static {
        STATIC_TILES_AVAILABLE.add(TILE_WIFI);
        STATIC_TILES_AVAILABLE.add(TILE_BLUETOOTH);
        STATIC_TILES_AVAILABLE.add(TILE_CELLULAR);
        STATIC_TILES_AVAILABLE.add(TILE_AIRPLANE);
        STATIC_TILES_AVAILABLE.add(TILE_ROTATION);
        STATIC_TILES_AVAILABLE.add(TILE_FLASHLIGHT);
        STATIC_TILES_AVAILABLE.add(TILE_LOCATION);
        STATIC_TILES_AVAILABLE.add(TILE_CAST);
        STATIC_TILES_AVAILABLE.add(TILE_INVERSION);
        STATIC_TILES_AVAILABLE.add(TILE_HOTSPOT);
        STATIC_TILES_AVAILABLE.add(TILE_NOTIFICATIONS);
        STATIC_TILES_AVAILABLE.add(TILE_DATA);
        STATIC_TILES_AVAILABLE.add(TILE_ROAMING);
        STATIC_TILES_AVAILABLE.add(TILE_DND);
        STATIC_TILES_AVAILABLE.add(TILE_APN);
        STATIC_TILES_AVAILABLE.add(TILE_VRTOXIN);
        STATIC_TILES_AVAILABLE.add(TILE_SCREENOFF);
        STATIC_TILES_AVAILABLE.add(TILE_SCREENSHOT);
        STATIC_TILES_AVAILABLE.add(TILE_BRIGHTNESS);
        STATIC_TILES_AVAILABLE.add(TILE_VOLUME);
        STATIC_TILES_AVAILABLE.add(TILE_EXPANDED_DESKTOP);
        STATIC_TILES_AVAILABLE.add(TILE_SCREEN_TIMEOUT);
        STATIC_TILES_AVAILABLE.add(TILE_USB_TETHERING);
        STATIC_TILES_AVAILABLE.add(TILE_AMBIENT_DISPLAY);
        STATIC_TILES_AVAILABLE.add(TILE_NFC);
        STATIC_TILES_AVAILABLE.add(TILE_HEADSUP);
        STATIC_TILES_AVAILABLE.add(TILE_REBOOT);
        STATIC_TILES_AVAILABLE.add(TILE_SYNC);
        STATIC_TILES_AVAILABLE.add(TILE_MUSIC);
        STATIC_TILES_AVAILABLE.add(TILE_ADB_NETWORK);
        STATIC_TILES_AVAILABLE.add(TILE_COMPASS);
        STATIC_TILES_AVAILABLE.add(TILE_LOCKSCREEN);
        STATIC_TILES_AVAILABLE.add(TILE_BATTERY_SAVER);
    }
}
