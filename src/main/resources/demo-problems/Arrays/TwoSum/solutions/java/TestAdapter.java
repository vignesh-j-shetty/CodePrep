import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;

public class TestAdapter implements com.codeprep.api.TestAdapter {
    @Override
    public JsonNode execute(JsonNode input, ObjectMapper mapper) {
        int[] nums = mapper.convertValue(input.get("nums"), int[].class);
        int target = input.get("target").asInt();
        return mapper.valueToTree(new Solution().twoSum(nums, target));
    }
}
