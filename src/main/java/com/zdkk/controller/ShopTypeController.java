package com.zdkk.controller;


import com.zdkk.dto.Result;
import com.zdkk.entity.ShopType;
import com.zdkk.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Autowired
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}
