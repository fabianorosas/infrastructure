#!groovy

nodePool = load 'infrastructure/pipeline/lib/nodePool.groovy'

def initialize() {
  properties([parameters([string(name: 'BAREMETAL_IP_ADDRESS',
				 defaultValue: "",
				 description: 'IP address of the baremetal machine to deploy'),
			  string(name: 'BAREMETAL_MAC_ADDRESS',
				 defaultValue: "",
				 description: 'MAC address of the network interface sending DHCP requests in the baremetal machine'),
			  string(name: 'BAREMETAL_DISK_SERIAL',
				 defaultValue: "",
				 description: 'Serial of the disk on which to install the system'),
			  string(name: 'BAREMETAL_TIMEZONE',
				 defaultValue: "",
				 description: 'Timezone to use during installtion'),
			  string(name: 'IPMI_IP_ADDRESS',
				 defaultValue: "",
				 description: 'IP address of the IPMI interface'),
			  string(name: 'IPMI_USER',
				 defaultValue: '""',
				 description: 'IPMI user'),
			  nonStoredPasswordParam(name: 'IPMI_PASSWORD',
						 description: 'IPMI ' +
						 'password'),
			 ]),
	     ])
}

def execute() {
  initialize()

  baremetal = [ipAddress: params.BAREMETAL_IP_ADDRESS,
	       macAddress: params.BAREMETAL_MAC_ADDRESS,
	       diskSerial: params.BAREMETAL_DISK_SERIAL,
	       timezone: params.BAREMETAL_TIMEZONE,
	       ipmiIpAddress: params.IPMI_IP_ADDRESS,
	       ipmiUser: params.IPMI_USER,
	       ipmiPassword: params.IPMI_PASSWORD,
	      ]

  nodePool.provisionNode(baremetal)
}


return this
