/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.velbus.internal.handler;

import static org.openhab.binding.velbus.internal.VelbusBindingConstants.PORT;

import java.io.IOException;
import java.io.InputStream;
import java.util.TooManyListenersException;

import org.apache.commons.io.IOUtils;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.io.transport.serial.PortInUseException;
import org.eclipse.smarthome.io.transport.serial.SerialPort;
import org.eclipse.smarthome.io.transport.serial.SerialPortEvent;
import org.eclipse.smarthome.io.transport.serial.SerialPortEventListener;
import org.eclipse.smarthome.io.transport.serial.SerialPortIdentifier;
import org.eclipse.smarthome.io.transport.serial.SerialPortManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link VelbusSerialBridgeHandler} is the handler for a Velbus Serial-interface and connects it to
 * the framework.
 *
 * @author Cedric Boon - Initial contribution
 */
public class VelbusSerialBridgeHandler extends VelbusBridgeHandler implements SerialPortEventListener {
    private Logger logger = LoggerFactory.getLogger(VelbusSerialBridgeHandler.class);

    private SerialPortManager serialPortManager;

    private SerialPortIdentifier portId;
    private SerialPort serialPort;

    private InputStream inputStream;

    public VelbusSerialBridgeHandler(Bridge velbusBridge, SerialPortManager serialPortManager) {
        super(velbusBridge);
        this.serialPortManager = serialPortManager;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        logger.debug("Serial port event triggered");

        if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            readPackets();
        }
    }

    @Override
    protected void connect() {
        String port = (String) getConfig().get(PORT);
        if (port != null) {
            // parse ports and if the port is found, initialize the reader
            portId = serialPortManager.getIdentifier(port);
            if (portId == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Port is not known!");
                return;
            }

            // initialize serial port
            try {
                serialPort = portId.open(getThing().getUID().toString(), 2000);
                initializeStreams(serialPort.getOutputStream(), serialPort.getInputStream());

                serialPort.addEventListener(this);
                serialPort.notifyOnDataAvailable(true);
                inputStream = serialPort.getInputStream();

                updateStatus(ThingStatus.ONLINE);
                logger.debug("Bridge online on serial port {}", port);
            } catch (final IOException ex) {
                onConnectionLost();
                logger.debug("I/O error on serial port {}", port);
            } catch (PortInUseException e) {
                onConnectionLost();
                logger.debug("Port {} is in use", port);
            } catch (TooManyListenersException e) {
                onConnectionLost();
                logger.debug("Cannot attach listener to port {}", port);
            }
        } else

        {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Serial port name not configured");
            logger.debug("Serial port name not configured");
        }
    }

    @Override
    protected void disconnect() {
        if (serialPort != null) {
            serialPort.close();
        }
        IOUtils.closeQuietly(inputStream);

        super.disconnect();
    }
}
