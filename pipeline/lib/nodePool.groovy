#!groovy

utils = load 'infrastructure/pipeline/lib/utils.groovy'

import groovy.transform.Field
import hudson.util.Secret

// TODO: fix wildcard imports
import jenkins.model.*
import hudson.model.*
import hudson.plugins.sshslaves.*
import hudson.slaves.*

@Field String defaultIsoFilePath = "/usr/share/isos/centos.iso"
@Field String infraRepoName = 'infrastructure'
@Field String ansiblePath = "$infraRepoName/ansible"

ansible = [inventory: "$ansiblePath/hosts.ini",
	   playbooks: [deploy: "$ansiblePath/bm-deploy.yaml",
		       jenkinsSlave: "$ansiblePath/jenkins-slave.yaml",
		      ],
	  ]

def provisionNode(Map node) {
  provisionNode(defaultIsoFilePath, node)
}

def provisionNode(String isoFilePath, Map node) {
  if (!node) {
    error('No nodes available to provision')
  }

  node.ipmiPasswordId = utils.addSecretString(
    node.ipmiPassword,
    "IPMI password for deployment of $node.ipAddress/$node.macAddress")
  node.remove('ipmiPassword')

  try {
    sh "ln -s $isoFilePath /tmp/${node.macAddress}_deploy.iso"
    deployNode(node)
  } finally {
    utils.removeCredentials(node.ipmiPasswordId)
    sh "rm -f /tmp/${node.macAddress}_deploy.iso"
  }

  createJenkinsSlave(node)
  node.status = "ready"
}

def deployNode(Map node){
  node.status = "deploying"

  sh "echo '[baremetal-ctrl]\nlocalhost' > '$ansible.inventory'"

  sh """cat <<EOF > $ansiblePath/vars-baremetal.yaml
baremetal:
  ip_address: $node.ipAddress
  mac_address: $node.macAddress
  disk_serial: $node.diskSerial
  timezone: $node.timezone
ipmi:
  user: $node.ipmiUsername
  ip_address: $node.ipAddress
EOF"""

  // We want the IPMI password to be passed via command line to
  // Ansible so that it is properly hidden by the plugin. With the
  // default hash_behaviour of 'replace', that would mean passing the
  // whole 'ipmi' dict, which would in turn lead to a mess with quotes
  // in the 'extraVars' argument below.
  // http://docs.ansible.com/ansible/intro_configuration.html#hash-behaviour
  sh """cat <<EOF > $ansiblePath/ansible.cfg
[defaults]
hash_behaviour = merge
EOF"""

  withCredentials([string(credentialsId: node.ipmiPasswordId,
			  variable: 'ipmiPassword')]) {

    ansiblePlaybook(playbook: ansible.playbooks.deploy,
		    inventory: ansible.inventory,
		    tags: 'deploy',
		    extras: '--user root',
		    extraVars: [
		      "ipmi.password": "$env.ipmiPassword",
		    ])
  }

  node.status = "deployed"
}

def createJenkinsSlave(Map node) {
  sh "echo '[jenkins-slave]\n$node.ipAddress' > '$ansible.inventory'"

  ansiblePlaybook(playbook: ansible.playbooks.jenkinsSlave,
		  inventory: ansible.inventory, tags: 'setup')

  addJenkinsNode(node)
}

def addJenkinsNode(Map node) {
  node = node + [name = '',
		 description = 'Dynamically added baremetal node',
		 userHome = '/home/jenkins',
		 numExecutors = 4,
		 label = 'builds_slave_bm_dyn',
		]

  Jenkins.instance.addNode(
    new DumbSlave(node.name, node.description, node.userHome,
		  node.numExecutors, Node.Mode.NORMAL, node.label,
		  new SSHLauncher(node.ipAddress, 22,
				  'jenkins-user-ssh-credentials',
				  null, null, null, null),
		  new RetentionStrategy.Always(),
		  new LinkedList()))
}

return this
