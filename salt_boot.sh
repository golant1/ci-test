#!/bin/bash

# Map hostnames of nodes and how it will be named for mcp
declare -A hostnames
hostnames=(["cz7935-kvm"]="kvm01.sriov-neutron.local" ["cz7936-kvm"]="kvm02.sriov-neutron.local" ["cz7937-kvm"]="kvm03.sriov-neutron.local" ["cz7903-kvm"]="cmp002.sriov-neutron.local" ["cz7904-kvm"]="cmp001.sriov-neutron.local")

echo "deb [arch=amd64] http://mirror.mirantis.com/proposed/saltstack-2016.3/xenial xenial main" >> /etc/apt/sources.list.d/mcp_saltstack.list
apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 0E08A149DE57BFBE
apt update

# Install salt-minion
apt-get install --yes salt-minion
HOSTNAME=$(hostname)

# Apply initial minion.conf
echo "id: ${hostnames[${HOSTNAME}]}" >>  /etc/salt/minion.d/minion.conf
echo "master: 10.109.9.196" >> /etc/salt/minion.d/minion.conf

# Restart salt-minion
service salt-minion stop && service salt-minion start

