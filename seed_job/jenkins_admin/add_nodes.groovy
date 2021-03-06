import jenkins.model.*
import hudson.model.User
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import hudson.model.Node.Mode
import hudson.slaves.DumbSlave
import hudson.slaves.*
import hudson.plugins.sshslaves.SSHLauncher
import hudson.plugins.sshslaves.verifiers.*

/*
Currently only support system credentials with global domain
*/
//Get credientials.json
def credentials_list_path = build.workspace.toString() + '/seed_job/files/nodes.json'
def inputFile = new File("$credentials_list_path")
def expected_nodes = new JsonSlurper().parse(inputFile)

expected_nodes.each{ expected_node->
    node = Jenkins.instance.getNode(expected_node.name)
    if (node) {
        println ("Node " + node.getNodeName() + " exists")
    }
    else{
        def node_launcher = ''
        def node_verify_strategy = ''
        def node_mode = ''
        def node_retention = ''
        if (expected_node?.mode == 'normal') {
            node_mode = Mode.NORMAL
        }
        else if (expected_node?.mode == 'exclusive') {
            node_mode = Mode.EXCLUSIVE
        }
        else {
            println 'Unsupported node mode'
            return
        }
        switch (expected_node?.retention?.type){
            case 'always':
                node_retention = new RetentionStrategy.Always()
                break
            case 'demand':
                node_retention = new RetentionStrategy.Demand(
                    inDemandDelay=expected_node?.retention?.in_demand_delay,
                    idleDela=expected_node?.retention?.idle_delay
                )
                break
            default:
                println 'Unsupported retention strategy'
        }
        if (expected_node?.launch_method?.type == "ssh_launcher") {
            switch (expected_node?.launch_method.hostkey_verify) {
                case 'known_hosts_file':
                    node_verify_strategy = new KnownHostsFileKeyVerificationStrategy() 
                    break
                case 'manually_provided':
                    println 'Manual host key verify strategy not supported in groovy'
                    return
                case 'manually_trusted':
                    println 'Manual host key verify strategy not supported in groovy'
                    return
                case 'non_verify':
                    node_verify_strategy = new NonVerifyingKeyVerificationStrategy() 
                    break
                default:
                    println 'Unknown host key verify strategy'
                    return
            }
            node_launcher = new SSHLauncher(
                host=expected_node?.launch_method?.host,
                port=expected_node?.launch_method?.port,
                credentialsId=expected_node?.launch_method?.credential_id,
                jvmOptions=expected_node?.launch_method?.jvm_options,
                javaPath=expected_node?.launch_method?.java_path,
                prefixStartSlaveCmd=expected_node?.launch_method?.prefix_start_slave_cmd,
                suffixStartSlaveCmd=expected_node?.launch_method?.suffix_start_slave_cmd,
                launchTimeoutSeconds=expected_node?.launch_method?.connection_timeout,
                maxNumRetries=expected_node?.launch_method?.maximum_number_of_retries,
                retryWaitTime=expected_node?.launch_method?.seconds_between_retries,
                sshHostKeyVerificationStrategy= node_verify_strategy
            )
        }
        else{
            return
        }
        node = new DumbSlave(name=expected_node.name,
            remoteFS=expected_node.remote_root_dir,
            launcher=node_launcher)
        node.setMode(node_mode)
        node.setRetentionStrategy(node_retention)
        node.setLabelString(expected_node?.label)
        node.setNodeDescription(expected_node?.description)
        node.setNumExecutors(expected_node?.executors)
        Jenkins.instance.addNode(node)
        Jenkins.instance.save()
    }
}

