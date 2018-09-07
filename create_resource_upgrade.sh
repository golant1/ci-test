#!/bin/bash

resource_name_list='tenant1 tenant2'
n=1

echo "openstack network create public --provider-network-type flat --provider-physical-network physnet1 --external"
echo "openstack subnet create --gateway 10.16.0.1 public 10.16.0.0/24"

for resource_name in $resource_name_list
do
  echo "openstack network create $resource_name"
  echo "openstack subnet create --dns-nameserver 8.8.8.8 $resource_name 192.168.${n}.0/24"
  ((n++))
done

#openstack network create
#neutron net-create tenant1
#neutron net-create tenant2
#neutron subnet-create --dns-nameserver 8.8.8.8 tenant1 192.168.1.0/24
#neutron net-create --shared --provider:network_type flat --provider:physical_network physnet1 --router:external=True ext

