package com.example.rehabilitationapp.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
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

    @Query("UPDATE users SET email = :email, birthday = :birthday, name = :name, gender = :gender, ui_style = :uiStyle WHERE user_id = :userId")
    void updateProfile(String userId, String email, String birthday, String name, String gender, String uiStyle);
}
