/*
 * Copyright (c) 2019-2020 Payara Foundation and/or its affiliates. All rights reserved.
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

import javax.enterprise.event.Event;
import java.io.File;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import javax.json.bind.annotation.JsonbPropertyOrder;
import javax.json.bind.annotation.JsonbTransient;
import javax.json.bind.config.PropertyOrderStrategy;

/**
 * Transient state of deployment process.
 * As deployment process progresses, various components update the state via {@link DeploymentProcess}, which in
 * turn updates this state.
 * <p>State is not modifiable outside of this package, but is not immutable (yet).
 * </p>
 */
@JsonbPropertyOrder(PropertyOrderStrategy.ANY)
public class DeploymentProcessState {
    private final String id;
    private final Namespace namespace;
    @JsonbTransient
    private final Instant start = Instant.now();
    private final String name;
    private boolean complete;
    private boolean failed;
    private String completionMessage;
    private Throwable failureCause;
    private volatile int version;
    @JsonbTransient
    private File tempLocation;
    @JsonbTransient
    private Set<Configuration> configurations = new LinkedHashSet<>();
    @JsonbTransient
    private URI persistentLocation;
    private String podName;
    private Instant completion;

    DeploymentProcessState(Namespace target, String name, File tempLocation) {
        this.id = UUID.randomUUID().toString();
        this.namespace = target;
        this.name = name;
        this.tempLocation = tempLocation;
    }

    /**
     * Opaque identifier of deployment
     * @return
     */
    public String getId() {
        return id;
    }

    /**
     * Version of the state. Each change of state increments the version.
     * Use this to confirm whether state referenced in the event corresponds to state the event was created in.
     * @return
     */
    public int getVersion() {
        return version;
    }

    /**
     * Target namespace of project
     * @return non-null namespace instance;
     */
    public Namespace getNamespace() {
        return namespace;
    }

    /**
     * The name of the uploaded artifact
     * @return name (incl. extension) of the file that was uploaded from client side
     */
    public String getName() {
        return name;
    }

    /**
     * Start if process.
     * @return non-null instant
     */
    public Instant getStart() {
        return start;
    }

    /**
     * Flag whether deployment is complete
     * @return true when process will likely proceed to next stage
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Flag whether deployment failed. {@link #getCompletionMessage()} contains detail message and
     * {@link #getFailureCause()} contains stacktrace of failure.
     * @return true when deployment is complete and not successful.
     */
    public boolean isFailed() {
        return failed;
    }

    /**
     * Final message of process.
     * @return non-null message for completed processes
     */
    public String getCompletionMessage() {
        return completionMessage;
    }

    /**
     * Throwable that caused failure
     * @return null for non-completed or not failed processes. Might be not null for failed.
     */
    public Throwable getFailureCause() {
        return failureCause;
    }

    /**
     * Local location of the artifact.
     * @return non-null local File reference for non-completed processes
     */
    public File getTempLocation() {
        return tempLocation;
    }

    /**
     * Configuration of artifact.
     * @return discovered configurations of the artifact
     */
    public Set<Configuration> getConfigurations() {
        return Collections.unmodifiableSet(configurations);
    }

    /**
     * Remote endpoint of completed deployment.
     * @return non-null http or https URI for successfully completed process
     */
    public URI getPersistentLocation() {
        return persistentLocation;
    }

    /**
     * Name of pod running the application
     * @return non-null resource name representing the running application container
     */
    public String getPodName() {
        return podName;
    }

    /**
     * Time of completion
     * @return non-null instant for completed process
     */
    public Instant getCompletion() {
        return completion;
    }
    
    CompletionStage<StateChanged> fireAsync(Event<StateChanged> event, ChangeKind kind) {
        return event.select(kind.asFilter()).fireAsync(new StateChanged(this, kind));
    }

    ChangeKind fail(String message, Throwable exception) {
        version++;
        this.completionMessage = message;
        this.failureCause = exception;
        this.complete = true;
        this.failed = true;
        this.completion = Instant.now();
        return ChangeKind.FAILED;
    }

    ChangeKind addConfiguration(Configuration configuration) {
        if (configurations.contains(configuration)) {
            throw new IllegalArgumentException("Matching configuration is already present");
        }
        version++;
        configurations.add(configuration);
        return ChangeKind.CONFIGURATION_ADDED;
    }

    ChangeKind setConfiguration(String kind, String id, boolean submit, Map<String, String> values) {
        var config = findConfiguration(kind, id);
        if (config.isPresent()) {
            config.get().updateConfiguration(values);
            if (submit && config.get().isComplete()) {
                config.get().setSubmitted(true);
            }
            version++;
            return ChangeKind.CONFIGURATION_SET;
        } else {
            throw new IllegalArgumentException("Configuration "+kind+"/"+id+" not present");
        }
    }

    private Optional<Configuration> findConfiguration(String kind, String id) {
        return configurations.stream().filter(c -> c.getKind().equals(kind) && c.getId().equals(id)).findAny();
    }

    ChangeKind transition(ChangeKind target) {
        version++;
        return target;
    }

    ChangeKind submitConfigurations(boolean force) {
        boolean allComplete = configurations.stream().allMatch(Configuration::isComplete);
        if (allComplete) {
            configurations.stream().forEach(c -> c.setSubmitted(true));
            version++;
            return ChangeKind.CONFIGURATION_FINISHED;
        } else if (force) {
            throw new IllegalStateException("Cannot submit configuration, not all configurations are complete");
        } else {
            return null;
        }
    }

    ChangeKind removePersistentLocation() {
        version++;
        persistentLocation = null;
        return null; // no event broadcasted
    }

    ChangeKind setPersistentLocation(URI location) {
        version++;
        this.persistentLocation = location;
        return ChangeKind.ARTIFACT_STORED;
    }
}
