package com.hotel.repositories;

import com.hotel.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    @Query("select count(user) from User user join user.roles role where role.id = :roleId")
    long countByRoleId(@Param("roleId") Long roleId);

    @Query("""
            select distinct user
            from User user
            left join UserProperty up
              on up.user = user
             and up.status = 'ACTIVE'
            where user.hotel.id in :hotelIds
               or up.hotel.id in :hotelIds
            """)
    List<User> findAccessibleUsers(@Param("hotelIds") Collection<Long> hotelIds);

    @Query("""
            select case when count(distinct user.id) > 0 then true else false end
            from User user
            left join UserProperty up
              on up.user = user
             and up.status = 'ACTIVE'
            where user.id = :userId
              and (user.hotel.id in :hotelIds or up.hotel.id in :hotelIds)
            """)
    boolean isUserAccessible(
            @Param("userId") Long userId,
            @Param("hotelIds") Collection<Long> hotelIds);
}
