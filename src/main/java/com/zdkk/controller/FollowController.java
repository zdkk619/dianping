package com.zdkk.controller;


import com.zdkk.dto.Result;
import com.zdkk.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    private IFollowService followService;
    @PutMapping("/{id}/{flag}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("flag") Boolean flag) {
        return followService.follow(id, flag);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable("id") Long id) {
        return followService.isFollowed(id);
    }

    @GetMapping("/common/{id}")
    public Result commonFollowers(@PathVariable("id") Long id) {
        return followService.commonFollowers(id);
    }
}
