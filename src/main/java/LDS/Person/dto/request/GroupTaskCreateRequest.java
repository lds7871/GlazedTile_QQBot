package LDS.Person.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 群组任务创建请求 DTO
 */
@Getter
@Setter
@ApiModel(description = "群组任务创建请求")
public class GroupTaskCreateRequest {
    
    @ApiModelProperty(value = "群组ID", required = true, example = "123456789")
    private String groupId;
}
