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

    @ColumnInfo(name = "need_sync")
    public int need_sync ;

    public int getLocalId() {
        return localId;
    }

    public void setLocalId(int localId) {
        this.localId = localId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedAtFormatted() {
        return createdAtFormatted;
    }

    public void setCreatedAtFormatted(String createdAtFormatted) {
        this.createdAtFormatted = createdAtFormatted;
    }

    public int getLoginStatus() {
        return loginStatus;
    }

    public void setLoginStatus(int loginStatus) {
        this.loginStatus = loginStatus;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getUiStyle() {
        return uiStyle;
    }

    public void setUiStyle(String uiStyle) {
        this.uiStyle = uiStyle;
    }

    @ColumnInfo(name = "gender")
    public String gender;          // "M" / "F"

    @ColumnInfo(name = "ui_style")
    public String uiStyle;         // "M" / "F"
}
