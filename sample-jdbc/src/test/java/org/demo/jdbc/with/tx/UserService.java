package org.demo.jdbc.with.tx;

import org.demo.annotation.Autowired;
import org.demo.annotation.Component;
import org.demo.annotation.Transactional;
import org.demo.jdbc.JdbcTemplate;
import org.demo.jdbc.JdbcTestBase;

@Component
@Transactional
public class UserService {

    @Autowired
    AddressService addressService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    public User createUser(String name, int age) {
        Number id = jdbcTemplate.updateAndReturnGeneratedKey(JdbcTestBase.INSERT_USER, name, age);
        User user = new User();
        user.id = id.intValue();
        user.name = name;
        user.theAge = age;
        return user;
    }

    public User getUser(int userId) {
        return jdbcTemplate.queryForObject(JdbcTestBase.SELECT_USER, User.class, userId);
    }

    public void updateUser(User user) {
        jdbcTemplate.update(JdbcTestBase.UPDATE_USER, user.name, user.theAge, user.id);
    }

    public void deleteUser(User user) {
        jdbcTemplate.update(JdbcTestBase.DELETE_USER, user.id);
        addressService.deleteAddress(user.id);
    }
}
