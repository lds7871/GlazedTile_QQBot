package LDS.Person.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 单条群组任务响应DTO
 */
@Getter
@Setter
@ApiModel(description = "单条群组任务响应")
public class GroupTaskResponse {
    @ApiModelProperty(value = "状态码", example = "0")
    private int code;

    @ApiModelProperty(value = "提示消息", example = "成功")
    private String message;

    @ApiModelProperty(value = "任务配置数据（包含所有字段）")
    private Map<String, Object> data;

    public GroupTaskResponse(int code, String message, Map<String, Object> data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static GroupTaskResponse success(Map<String, Object> data) {
        return new GroupTaskResponse(0, "成功", data);
    }

    public static GroupTaskResponse notFound(String message) {
        return new GroupTaskResponse(1, message, null);
    }

    public static GroupTaskResponse error(String message) {
        return new GroupTaskResponse(-1, message, null);
    }
}
