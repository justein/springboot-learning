package com.nova.lyn.redis.service.impl;

import com.nova.lyn.redis.service.SeckillService;

import java.util.HashMap;
import java.util.Map;

/***
 * @ClassName: SeckillServiceImpl
 * @Description: TODO
 * @Author: Lyn
 * @Date: 2019/3/26 下午9:37
 * @version : V1.0
 */
public class SeckillServiceImpl implements SeckillService {

    static Map<String,Integer> products;//模拟商品信息表
    static Map<String,Integer> stock;//模拟库存表
    static Map<String,String> orders;//模拟下单成功用户表
    static {
        /**
         * 模拟多个表，商品信息表，库存表，秒杀成功订单表
         */
        products = new HashMap<>();
        stock = new HashMap<>();
        orders = new HashMap<>();
        products.put("123456",100000);
        stock.put("123456",100000);
    }

    private String queryMap(String productId){//模拟查询数据库
        return "国庆活动，iPhone100，限量"
                +products.get(productId)
                +"台,还剩:"+stock.get(productId)
                +"台,该商品成功下单用户数:"
                +orders.size()+"人";
    }

    @Override
    public String queryProductStore(String productId) {
        return this.queryMap(productId);
    }


}
