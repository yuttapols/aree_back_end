package com.roti5dao.common.storage;

/** โฟลเดอร์เก็บไฟล์ — public เสิร์ฟผ่าน GET /files/** ส่วน private (สลิป) เสิร์ฟผ่าน endpoint ที่ตรวจสิทธิ์เท่านั้น */
public enum StorageFolder {
    PRODUCTS("products", true),
    AVATARS("avatars", true),
    BANNERS("banners", true),
    SLIPS("slips", false);

    private final String dir;
    private final boolean isPublic;

    StorageFolder(String dir, boolean isPublic) {
        this.dir = dir;
        this.isPublic = isPublic;
    }

    public String dir() {
        return dir;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public static boolean isPublicDir(String dir) {
        for (StorageFolder f : values()) {
            if (f.isPublic && f.dir.equals(dir)) {
                return true;
            }
        }
        return false;
    }
}
