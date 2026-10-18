package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.ui.tools.ToolArgs

/**
 * Dispatcher for the 23 unit-converter tools. Returns false when the tool id
 * does not belong to this batch so the host can try another dispatcher.
 */
@Composable
fun ConvertTools(args: ToolArgs): Boolean {
    when (args.tool.id) {
        "convert.acceleration" -> AccelerationToolScreen(args)
        "convert.angle" -> AngleToolScreen(args)
        "convert.area" -> AreaToolScreen(args)
        "convert.cooking" -> CookingToolScreen(args)
        "convert.data_storage" -> DataStorageToolScreen(args)
        "convert.data_transfer" -> DataTransferToolScreen(args)
        "convert.energy" -> EnergyToolScreen(args)
        "convert.flow" -> FlowToolScreen(args)
        "convert.force" -> ForceToolScreen(args)
        "convert.fuel" -> FuelToolScreen(args)
        "convert.length" -> LengthToolScreen(args)
        "convert.numeric_base" -> NumericBaseToolScreen(args)
        "convert.power" -> PowerToolScreen(args)
        "convert.pressure" -> PressureToolScreen(args)
        "convert.ring_size" -> RingSizeToolScreen(args)
        "convert.roman" -> RomanToolScreen(args)
        "convert.shoe_size" -> ShoeSizeToolScreen(args)
        "convert.speed" -> SpeedToolScreen(args)
        "convert.temperature" -> TemperatureToolScreen(args)
        "convert.time" -> TimeToolScreen(args)
        "convert.torque" -> TorqueToolScreen(args)
        "convert.volume" -> VolumeToolScreen(args)
        "convert.weight" -> WeightToolScreen(args)
        else -> return false
    }
    return true
}
