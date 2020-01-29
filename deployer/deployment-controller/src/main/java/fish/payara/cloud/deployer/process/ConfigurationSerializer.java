/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 *  Copyright (c) [2020] Payara Foundation and/or its affiliates. All rights reserved.
 * 
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License.  You can
 *  obtain a copy of the License at
 *  https://github.com/payara/Payara/blob/master/LICENSE.txt
 *  See the License for the specific
 *  language governing permissions and limitations under the License.
 * 
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License.
 * 
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/legal/LICENSE.txt.
 * 
 *  GPL Classpath Exception:
 *  The Payara Foundation designates this particular file as subject to the "Classpath"
 *  exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *  file that accompanied this code.
 * 
 *  Modifications:
 *  If applicable, add the following below the License Header, with the fields
 *  enclosed by brackets [] replaced by your own identifying information:
 *  "Portions Copyright [year] [name of copyright owner]"
 * 
 *  Contributor(s):
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 */
package fish.payara.cloud.deployer.process;

import fish.payara.cloud.deployer.process.Configuration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;

/**
 * Serialises a {@link Configuration} to JSON format.
 * Resulting format is in the form:
 * <pre>
 * {
 *  "$id": {
 *      "keys": [
 *        {
 *          "name": "$key",
 *          "required": "$isRequired",
 *          "default": "$defaultValue" (if present)
 *        },
 *         ...
 *      ],
 *      "values": {
 *        "$key: $value,
 *        ...
 *      }
 *  },
 * }
 * </pre>
 * @author jonathan coustick
 */
public class ConfigurationSerializer implements JsonbSerializer<Configuration> {

    @Override
    public void serialize(Configuration config, JsonGenerator generator, SerializationContext ctx) {
        Map<String, String> values = new HashMap<>();
        
        generator.writeStartObject();
        generator.writeStartObject(config.getId());
        generator.writeStartArray("keys");
        for (String key : config.getKeys()) {
            generator.writeStartObject();
            generator.write("name", key);
            generator.write("required", config.isRequired(key));
            Optional<String> defaultValue = config.getDefaultValue(key);
            if (defaultValue.isPresent()) {
                generator.write("default", defaultValue.get());
            }
            generator.writeEnd();
            Optional<String> value = config.getValue(key);
            if (value.isPresent()) {
                values.put(key, value.get());
            }
        }        
        generator.writeEnd(); //End of keys
        generator.writeStartObject("values");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            generator.write(entry.getKey(), entry.getValue());
        }
        generator.writeEnd(); //End of values
        generator.writeEnd(); //End of id object
        generator.writeEnd(); //End of object
        generator.flush();
    }
    
}
