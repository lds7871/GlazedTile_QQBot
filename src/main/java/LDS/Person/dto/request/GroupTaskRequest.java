package LDS.Person.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 群组任务查询请求DTO
 */
@Getter
@Setter
@ApiModel(description = "群组任务查询请求")
public class GroupTaskRequest {
    @ApiModelProperty(value = "群组ID", required = true, example = "123456789")
    private String groupId;
}
