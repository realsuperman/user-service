package com.example.demo.client;

import com.example.demo.vo.ResponseOrder;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name="order-service") // 호출할 모듈 이름(디스커버리 서버에 등록될 이름)
public interface OrderServiceClient {
    @GetMapping("/order-service/{userId}/orders") // 호출한 모듈의 URL
    List<ResponseOrder> getOrders(@PathVariable("userId") String userId);
}
