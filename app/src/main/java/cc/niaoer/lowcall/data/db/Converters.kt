package cc.niaoer.lowcall.data.db

import androidx.room.TypeConverter
import cc.niaoer.lowcall.data.model.CallAction
import cc.niaoer.lowcall.data.model.RuleType

class Converters {
    @TypeConverter
    fun fromRuleType(value: RuleType): String = value.name

    @TypeConverter
    fun toRuleType(value: String): RuleType = RuleType.valueOf(value)

    @TypeConverter
    fun fromCallAction(value: CallAction): String = value.name

    @TypeConverter
    fun toCallAction(value: String): CallAction = CallAction.valueOf(value)
}
