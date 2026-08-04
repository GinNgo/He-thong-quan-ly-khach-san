package com.hotel.repositories;

import com.hotel.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByPendingEmailIgnoreCase(String pendingEmail);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
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

    @Query("""
            select distinct user
            from User user join user.roles role
            where role.code = 'CUSTOMER'
              and upper(user.status) = 'ACTIVE'
              and (:query = '' or lower(user.fullName) like lower(concat('%', :query, '%'))
                   or lower(user.username) like lower(concat('%', :query, '%'))
                   or lower(user.email) like lower(concat('%', :query, '%')))
            order by user.fullName, user.id
            """)
    List<User> searchActiveCustomers(@Param("query") String query, Pageable pageable);
}
