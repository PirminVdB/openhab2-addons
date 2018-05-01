/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.velbus.handler;

import static org.openhab.binding.velbus.VelbusBindingConstants.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.measure.quantity.Illuminance;
import javax.measure.quantity.Length;
import javax.measure.quantity.Speed;

import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.MetricPrefix;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.velbus.internal.packets.VelbusPacket;
import org.openhab.binding.velbus.internal.packets.VelbusSensorReadoutRequestPacket;

/**
 * The {@link VelbusVMBMeteoHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Cedric Boon - Initial contribution
 */
public class VelbusVMBMeteoHandler extends VelbusTemperatureSensorHandler {
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = new HashSet<>(Arrays.asList(THING_TYPE_VMBMETEO));

    private static final byte RAIN_SENSOR_CHANNEL = 0x02;
    private static final byte LIGHT_SENSOR_CHANNEL = 0x04;
    private static final byte WIND_SENSOR_CHANNEL = 0x08;
    private static final byte ALL_SENSOR_CHANNELS = RAIN_SENSOR_CHANNEL | LIGHT_SENSOR_CHANNEL | WIND_SENSOR_CHANNEL;

    private ChannelUID rainfallChannel;
    private ChannelUID illuminanceChannel;
    private ChannelUID windspeedChannel;

    public VelbusVMBMeteoHandler(Thing thing) {
        super(thing, 0, new ChannelUID(thing.getUID(), "CH10"));

        this.rainfallChannel = new ChannelUID(thing.getUID(), "CH11");
        this.illuminanceChannel = new ChannelUID(thing.getUID(), "CH12");
        this.windspeedChannel = new ChannelUID(thing.getUID(), "CH13");
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        super.handleCommand(channelUID, command);

        VelbusBridgeHandler velbusBridgeHandler = getVelbusBridgeHandler();
        if (velbusBridgeHandler == null) {
            logger.warn("Velbus bridge handler not found. Cannot handle command without bridge.");
            return;
        }

        if (command instanceof RefreshType) {
            if (channelUID.equals(rainfallChannel)) {
                sendSensorReadoutRequest(velbusBridgeHandler, RAIN_SENSOR_CHANNEL);
            } else if (channelUID.equals(illuminanceChannel)) {
                sendSensorReadoutRequest(velbusBridgeHandler, LIGHT_SENSOR_CHANNEL);
            } else if (channelUID.equals(windspeedChannel)) {
                sendSensorReadoutRequest(velbusBridgeHandler, WIND_SENSOR_CHANNEL);
            }
        }
    }

    @Override
    protected void sendSensorReadoutRequest(VelbusBridgeHandler velbusBridgeHandler) {
        super.sendSensorReadoutRequest(velbusBridgeHandler);

        sendSensorReadoutRequest(velbusBridgeHandler, ALL_SENSOR_CHANNELS);
    }

    protected void sendSensorReadoutRequest(VelbusBridgeHandler velbusBridgeHandler, byte channel) {
        VelbusSensorReadoutRequestPacket packet = new VelbusSensorReadoutRequestPacket(getModuleAddress().getAddress(),
                channel);

        byte[] packetBytes = packet.getBytes();
        velbusBridgeHandler.sendPacket(packetBytes);
    }

    @Override
    public void onPacketReceived(byte[] packet) {
        super.onPacketReceived(packet);

        logger.trace("onPacketReceived() was called");

        if (packet[0] == VelbusPacket.STX && packet.length >= 5) {
            byte command = packet[4];

            if (command == COMMAND_SENSOR_RAW_DATA && packet.length >= 10) {
                byte highByteCurrentRainValue = packet[5];
                byte lowByteCurrentRainValue = packet[6];
                byte highByteCurrentLightValue = packet[7];
                byte lowByteCurrentLightValue = packet[8];
                byte highByteCurrentWindValue = packet[9];
                byte lowByteCurrentWindValue = packet[10];

                double rainValue = (highByteCurrentRainValue * 0x100 + lowByteCurrentRainValue) / 10;
                double lightValue = (highByteCurrentLightValue * 0x100 + lowByteCurrentLightValue);
                double windValue = (highByteCurrentWindValue * 0x100 + lowByteCurrentWindValue) / 10;

                QuantityType<Length> rainValueState = new QuantityType<>(rainValue, MetricPrefix.MILLI(SIUnits.METRE));
                QuantityType<Illuminance> lightValueState = new QuantityType<>(lightValue, SmartHomeUnits.LUX);
                QuantityType<Speed> windValueState = new QuantityType<>(windValue, SIUnits.KILOMETRE_PER_HOUR);

                updateState(rainfallChannel, rainValueState);
                updateState(illuminanceChannel, lightValueState);
                updateState(windspeedChannel, windValueState);
            }
        }
    }
}