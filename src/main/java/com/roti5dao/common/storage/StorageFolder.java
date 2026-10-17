package com.roti5dao.common.storage;

/** โฟลเดอร์เก็บรูป — ทุกโฟลเดอร์เป็นรูปสาธารณะ (ดิสก์เสิร์ฟผ่าน GET /files/**) */
public enum StorageFolder {
    PRODUCTS("products"),
    AVATARS("avatars"),
    BANNERS("banners");

    private final String dir;

    StorageFolder(String dir) {
        this.dir = dir;
    }

    public String dir() {
        return dir;
    }
}
