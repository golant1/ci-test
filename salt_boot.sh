#!/bin/bash

# Map hostnames of nodes and how it will be named for mcp
declare -A hostnames
hostnames=(["cz7935-kvm"]="kvm01.sriov-neutron.local" ["cz7936-kvm"]="kvm02.sriov-neutron.local" ["cz7937-kvm"]="kvm03.sriov-neutron.local" ["cz7903-kvm"]="cmp002.sriov-neutron.local" ["cz7904-kvm"]="cmp001.sriov-neutron.local")

echo "deb [arch=amd64] http://mirror.mirantis.com/proposed/saltstack-2017.7/xenial xenial main" >> /etc/apt/sources.list.d/mcp_saltstack.list
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


#initial setup
#neutron net-create --shared --provider:network_type vlan --provider:physical_network physnet2 --provider:segmentation_id 1001 --router:external=True public
#neutron subnet-create --gateway 10.16.0.1 public 10.16.0.0/24
#neutron router-create r1
#neutron router-gateway-set r1 public
#neutron router-interface-add r1 <UUID of internal subnet>
#
#neutron net-create tenant1
#neutron subnet-create --dns-nameserver 8.8.8.8 tenant1 192.168.1.0/24
#openstack floating ip create public

#wget http://download.cirros-cloud.net/0.3.5/cirros-0.3.5-x86_64-disk.img
#glance image-create --name cirros --visibility public --disk-format qcow2 --container-format bare --file cirros-0.3.5-x86_64-disk.img --progress
#nova flavor-create m1.extra_tiny auto 256 0 1
#nova boot --flavor m1.extra_tiny --image cirros --nic net-name=tenant1 tenant1-1

#neutron floatingip-associate FLOATINGIP_ID PORT

#on CTL node
#ip tunnel add tun0 mode ipip remote 10.109.9.196 local 10.109.9.194 dev ens2
#ifconfig tun0 20.0.0.2 netmask 255.255.255.252 pointopoint 20.0.0.1
#ifconfig tun0 mtu 1492 up
