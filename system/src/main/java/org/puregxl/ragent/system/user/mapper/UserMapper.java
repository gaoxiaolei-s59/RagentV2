package org.puregxl.ragent.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.puregxl.ragent.system.user.entity.UserDO;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
