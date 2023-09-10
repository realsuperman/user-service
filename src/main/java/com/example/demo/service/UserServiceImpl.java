package com.example.demo.service;

import com.example.demo.client.OrderServiceClient;
import com.example.demo.dto.UserDto;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.UserRepository;
import com.example.demo.vo.ResponseOrder;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private Environment env;
    RestTemplate restTemplate;
    OrderServiceClient orderServiceClient;
    CircuitBreakerFactory circuitBreakerFactory;
    // 특정 모듈 호출 실패시에도 유저 모듈은 불러올 수 있어야 한다 따라서 CuicuitBreaker를 만든다

    public UserServiceImpl(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder,
                           Environment env, RestTemplate restTemplate, OrderServiceClient orderServiceClient,
                           CircuitBreakerFactory circuitBreakerFactory) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.env=env;
        this.restTemplate=restTemplate;
        this.orderServiceClient=orderServiceClient;
        this.circuitBreakerFactory=circuitBreakerFactory;
    }

    @Override
    public UserDto createUser(UserDto userDto) {
        userDto.setUserId(UUID.randomUUID().toString());

        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        UserEntity userEntity = mapper.map(userDto, UserEntity.class);
        userEntity.setEncryptedPwd(passwordEncoder.encode(userDto.getPwd()));

        userRepository.save(userEntity);

        return mapper.map(userEntity, UserDto.class);
    }

    @Override
    public UserDto getUserByUserId(String userId) {
        UserEntity userEntity = userRepository.findByUserId(userId);

        if (userEntity == null) throw new NoSuchElementException("User not found");

        UserDto userDto = new ModelMapper().map(userEntity, UserDto.class);
//        String orderUrl = String.format(env.getProperty("order_service.url"),userId);
//        ResponseEntity<List<ResponseOrder>> orderListResponse =
//                restTemplate.exchange(orderUrl, HttpMethod.GET, null,
//                        new ParameterizedTypeReference<List<ResponseOrder>>() {});
//        List<ResponseOrder> ordersList = orderListResponse.getBody();

        /* Using a feign client */
        /* Feign Exception Handling */

//        try {
//            ordersList = orderServiceClient.getOrders(userId);
//        }catch(FeignException.FeignClientException e) {
//            log.error(e.getMessage());
//        }

        //List<ResponseOrder> ordersList = orderServiceClient.getOrders(userId);

        log.info("Before call orders microservice");
        CircuitBreaker circuitbreaker = circuitBreakerFactory.create("circuitbreaker"); // 서킷브레이커 만듬
        // 특정 메소드 실행 후 성공하면 해당 결과를 반환하고 실패하면 빈 arrayList 객체를 반환한다
        List<ResponseOrder> ordersList = circuitbreaker.run(() -> orderServiceClient.getOrders(userId),
                throwable -> new ArrayList<>());
        log.info("After call orders microservice");
        userDto.setOrders(ordersList);
        return userDto;

    }

    @Override
    public Iterable<UserEntity> getUserByAll() {
        return userRepository.findAll();
    }

    @Override
    public UserDto getUserDetailsByEmail(String email) {
        UserEntity userEntity = userRepository.findByEmail(email);
        if(userEntity == null) throw new UsernameNotFoundException(email);
        UserDto userDto = new ModelMapper().map(userEntity, UserDto.class);
        return userDto;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity userEntity = userRepository.findByEmail(username);
        if (userEntity == null) throw new UsernameNotFoundException(username);
        return new User(userEntity.getEmail(),userEntity.getEncryptedPwd(),true,true,true,true,new ArrayList<>());
    }
}
