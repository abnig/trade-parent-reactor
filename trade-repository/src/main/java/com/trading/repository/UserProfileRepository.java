package com.trading.repository;

import com.trading.model.ProfileChanges;
import com.trading.model.ProfileCredentials;
import com.trading.model.UserProfile;
import java.util.Optional;

public interface UserProfileRepository {
    Optional<UserProfile> find(long userId);
    Optional<ProfileCredentials> lockCredentials(long userId);
    void update(long userId, ProfileChanges changes, boolean emailChanged);
}
