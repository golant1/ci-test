#!/bin/bash

router_type_list='ha dvr legacy'
n=1
i=110

neutron router-create r_ha --ha=true
openstack router create --distributed r_dvr
neutron router-create r_legacy --ha=false

for router_type in $router_type_list
do
  openstack network create network_tenant_${router_type}
  openstack network create network_external_${router_type} --provider-network-type vxlan --provider-segment ${i} --external
  openstack subnet create --dhcp network_external_${router_type}_subnet --network network_external_${router_type} --subnet-range 10.10.${i}.1/24 --gateway 10.10.${i}.1
  subnet_tenant_ha=`openstack subnet create --dhcp tenant_${router_type}_subnet --network network_tenant_${router_type} --subnet-range 192.168.${i}.0/24 -c id -f value`
  neutron router-gateway-set r_${router_type} network_external_${router_type}
  neutron router-interface-add r_${router_type} ${subnet_tenant_ha}

  ((i++))
done
