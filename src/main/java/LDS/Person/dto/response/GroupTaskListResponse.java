package LDS.Person.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 群组任务列表响应DTO
 */
@Getter
@Setter
@ApiModel(description = "群组任务列表响应")
public class GroupTaskListResponse {
    @ApiModelProperty(value = "状态码", example = "0")
    private int code;

    @ApiModelProperty(value = "提示消息", example = "成功")
    private String message;

    @ApiModelProperty(value = "任务配置数据列表")
    private List<Map<String, Object>> data;

    public GroupTaskListResponse(int code, String message, List<Map<String, Object>> data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static GroupTaskListResponse success(List<Map<String, Object>> data) {
        return new GroupTaskListResponse(0, "成功", data);
    }

    public static GroupTaskListResponse error(String message) {
        return new GroupTaskListResponse(-1, message, null);
    }
}
