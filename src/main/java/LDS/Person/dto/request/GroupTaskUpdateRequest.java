package LDS.Person.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 群组任务更新请求DTO
 */
@Getter
@Setter
@ApiModel(description = "群组任务更新请求")
public class GroupTaskUpdateRequest {
    @ApiModelProperty(value = "群组ID", required = true, example = "123456789")
    private String groupId;

    @ApiModelProperty(value = "任务字段名称", required = true, example = "morning")
    private String taskName;

    @ApiModelProperty(value = "任务状态（true 或 false，会自动转为 1 或 0）", required = true, example = "true")
    private Boolean taskStatus;
}
