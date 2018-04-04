/*
 * The MIT License
 *
 * Copyright 2018 hughsaunders.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.rackspace.jenkins_nodepool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.Queue.Task;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Top level Jenkins configuration class to manage all NodePool configuration
 */

@Extension
public class NodePools extends GlobalConfiguration implements Iterable<NodePool> {

    /**
     * Our class logger.
     **/
    private static final Logger LOG = Logger.getLogger(NodePools.class.getName());

    public static NodePools get() {
        return GlobalConfiguration.all().get(NodePools.class);
    }

    /**
     * Default timeout is 5 minutes
     */
    public static final int DEFAULT_TIMEOUT_SEC = 300;

    private List<NodePool> nodePools;

    public NodePools() {
        load();
        initTransients();

    }

    // Called for deserialisation
    public void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject(); // call default deserializer
        initTransients();
    }

    private void initTransients() {
        if (nodePools == null) {
            nodePools = new ArrayList();
        }
    }


    @Override
    @SuppressFBWarnings("UC_USELESS_OBJECT")
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        List<NodePool> removedNodePools = new ArrayList(nodePools);
        // req.bindJSON is the only thing the super method does.
        // We need to store the old list before binding the new
        // data so we can't use the super() call.
        req.bindJSON(this, json);
        if (!json.containsKey("nodePools")) {
            // if the last nodepool configuration is removed
            // the nodePools field is not included in the json
            // so the field is not updated. This means the
            // last configuration will appear to be removed
            // in the ui, but will reappear on refresh. :(
            // To prevent that we clear the array if no
            // nodePools are supplied.
            nodePools.clear();
        }
        save();
        removedNodePools.removeAll(nodePools);
        // removedNodePools now contains all the nodePools
        // that have been removed. They should be cleaned up
        // so connections are closed etc
        removedNodePools.forEach((np) -> np.cleanup());
        return true;
    }

    public List<NodePool> getNodePools() {
        return nodePools;
    }

    @Override
    public Iterator<NodePool> iterator() {
        return nodePools.iterator();
    }
    public List<NodePool> nodePoolsForLabel(Label label) {
        return stream()
                .filter((NodePool np) -> label.getName().startsWith(np.getLabelPrefix()))
                .collect(Collectors.toList());
    }

    /**
     * Submit request for node(s) required to execute the given task based on the nodes associated with the specified
     * label. Uses a default timeout of 60 seconds.
     *
     * @param label the label attribute to filter the list of available nodes
     * @param task  the task to execute
     * @throws IllegalArgumentException    if timeout is less than 1 second
     * @throws NodeRequestTimeoutException if the node request timed out
     * @throws Exception                   if an error occurs managing the provision components
     */
    public void provisionNode(Label label, Task task) throws IllegalArgumentException, NodeRequestTimeoutException, Exception {
        provisionNode(label, task, DEFAULT_TIMEOUT_SEC);
    }

    /**
     * Submit request for node(s) required to execute the given task based on the nodes associated with the specified
     * label.
     *
     * @param label        the label attribute to filter the list of available nodes
     * @param task         the task to execute
     * @param timeoutInSec the timeout in seconds to provision the node(s)
     * @throws IllegalArgumentException    if timeout is less than 1 second
     * @throws NodeRequestTimeoutException if the node request timed out
     * @throws Exception                   if an error occurs managing the provision components
     */
    public void provisionNode(Label label, Task task, int timeoutInSec) throws IllegalArgumentException, NodeRequestTimeoutException, Exception {
        for (NodePool np : nodePoolsForLabel(label)) {
            np.provisionNode(label, task, timeoutInSec);
            // Prevent multiple nodes being provisioned if label prefixes were to overlap.
            break;
        }
    }

    @DataBoundSetter
    public void setNodePools(List<NodePool> nodePools) {
        this.nodePools = nodePools;
    }

    public Stream<NodePool> stream() {
        return nodePools.stream();
    }
}
