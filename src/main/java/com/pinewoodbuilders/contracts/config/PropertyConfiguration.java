/*
 * Copyright (c) 2018.
 *
 * This file is part of Xeus.
 *
 * Xeus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeus.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.pinewoodbuilders.contracts.config;

import com.pinewoodbuilders.contracts.debug.Evalable;
import com.pinewoodbuilders.exceptions.FailedToLoadPropertiesConfigurationException;

import java.io.IOException;
import java.util.Properties;

public abstract class PropertyConfiguration extends Evalable {

    /**
     * The properties holder, the object can be used
     * to pull out the loaded properties values.
     */
    protected final Properties properties = new Properties();

    /**
     * Loads the properties file using the given class loader and sets the
     * value to the {@link PropertyConfiguration#properties properties} object.
     *
     * @param classLoader      The class loader that should be used to load the properties file.
     * @param propertyFileName The name of the properties file that should be loaded.
     */
    protected void loadProperty(ClassLoader classLoader, String propertyFileName) {
        try {
            properties.load(
                classLoader.getResourceAsStream(propertyFileName)
            );
        } catch (IOException e) {
            throw new FailedToLoadPropertiesConfigurationException("Failed to load " + propertyFileName, e);
        }
    }
}
