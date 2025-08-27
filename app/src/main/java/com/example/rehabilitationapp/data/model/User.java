// User.java
package com.example.rehabilitationapp.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    public int localId;

    @ColumnInfo(name = "user_id")
    public String userId;

    @ColumnInfo(name = "password")
    public String password;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "created_at_formatted")
    public String createdAtFormatted;

    // ===== 新增欄位 =====
    @ColumnInfo(name = "login_status", defaultValue = "0")
    public int loginStatus;        // 0=未登入, 1=已登入

    @ColumnInfo(name = "email")
    public String email;

    @ColumnInfo(name = "birthday")
    public String birthday;        // 建議字串 YYYY/MM/DD

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "gender")
    public String gender;          // "M" / "F"

    @ColumnInfo(name = "ui_style")
    public String uiStyle;         // "M" / "F"
}
