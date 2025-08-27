package com.example.rehabilitationapp.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.rehabilitationapp.data.model.User;
@Dao
public interface UserDao {

    @Insert
    void insert(User user);

    @Update
    void update(User user);

    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1")
    User findById(String userId);

    @Query("UPDATE users SET login_status = :status WHERE user_id = :userId")
    void updateLoginStatus(String userId, int status);

    @Query("SELECT COUNT(*) FROM users WHERE login_status = 1")
    int countLoggedIn();

    // 用 user_id 當查詢鍵，ui_style 用 String（"M"/"F"）
    @Query("UPDATE users SET email = :email, birthday = :birthday, name = :name, gender = :gender, ui_style = :uiStyle WHERE user_id = :userId")
    void updateProfile(String userId, String email, String birthday, String name, String gender, String uiStyle);

    @Query("SELECT * FROM users WHERE login_status = 1 LIMIT 1")
    User findLoggedInOne();

    // ——把下面三個 uid 版本刪掉或改成 user_id 版本——
    // LiveData 版：若需要觀察某一位使用者
    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1")
    LiveData<User> observeUser(String userId);

    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1")
    User getUserSync(String userId);

    // 以 user_id 更新 ui_style，且型別一致用 String
    @Query("UPDATE users SET ui_style = :style WHERE user_id = :userId")
    void updateUiStyle(String userId, String style);
}

