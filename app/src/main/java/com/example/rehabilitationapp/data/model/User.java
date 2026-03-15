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

    // ===== 登入狀態 =====
    @ColumnInfo(name = "login_status", defaultValue = "0")
    public int loginStatus;        // 0=未登入, 1=已登入

    @ColumnInfo(name = "email")
    public String email;

    @ColumnInfo(name = "birthday")
    public String birthday;        // YYYY/MM/DD

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "need_sync")
    public int need_sync;

    @ColumnInfo(name = "gender")
    public String gender;          // "M" / "F"

    @ColumnInfo(name = "ui_style")
    public String uiStyle;         // "M" / "F"

    // ===== 新增：雙軌登入欄位 =====

    /** 帳號類型: "formal"=A軌正式帳號, "quick"=B軌暱稱快速登入 */
    @ColumnInfo(name = "account_type", defaultValue = "formal")
    public String accountType;

    /** B軌問卷：年齡範圍（如 "20-29", "30-39", "40-49" ...） */
    @ColumnInfo(name = "age_range")
    public String ageRange;

    /** B軌問卷：術後天數 */
    @ColumnInfo(name = "post_surgery_days", defaultValue = "-1")
    public int postSurgeryDays;

    /** B軌問卷：診斷類型 */
    @ColumnInfo(name = "diagnosis_type")
    public String diagnosisType;

    // ===== Getter / Setter =====

    public int getLocalId() { return localId; }
    public void setLocalId(int localId) { this.localId = localId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getCreatedAtFormatted() { return createdAtFormatted; }
    public void setCreatedAtFormatted(String createdAtFormatted) { this.createdAtFormatted = createdAtFormatted; }

    public int getLoginStatus() { return loginStatus; }
    public void setLoginStatus(int loginStatus) { this.loginStatus = loginStatus; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getUiStyle() { return uiStyle; }
    public void setUiStyle(String uiStyle) { this.uiStyle = uiStyle; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getAgeRange() { return ageRange; }
    public void setAgeRange(String ageRange) { this.ageRange = ageRange; }

    public int getPostSurgeryDays() { return postSurgeryDays; }
    public void setPostSurgeryDays(int postSurgeryDays) { this.postSurgeryDays = postSurgeryDays; }

    public String getDiagnosisType() { return diagnosisType; }
    public void setDiagnosisType(String diagnosisType) { this.diagnosisType = diagnosisType; }
}
