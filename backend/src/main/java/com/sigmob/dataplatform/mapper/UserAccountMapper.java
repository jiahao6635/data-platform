package com.sigmob.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sigmob.dataplatform.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}