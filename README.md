# DNS wildcard for AWS Elastic Load Balancer
I came across an use case where I rapidly create/destroy ELBs for testing/demoing,
and I needed those ELBs to support DNS wildcard.

That is, I wanted `*.NAME-ID.REGION.elb.amazonaws.com` to all land on the same ELB, so
that my `haproxy` that's sitting behind ELB can direct traffic to the right backend.

`*.NAME-ID.REGION.elb.amazonaws.com` doesn't actually work, so I needed to write
a custom DNS server where `*.NAME-ID.REGION.elb.kohsuke.org` would be valid CNAME
for `NAME-ID.REGION.elb.amazonaws.com`.

This project implements that custom DNS server. You can run this like the following:

```
sudo java -jar elb-dns.jar -b elb.kohsuke.org
```

Here is my Upstart config file:

```
description "Run ELB alias DNS"
author "Kohsuke Kwaguchi"

start on runlevel [2345]
stop on runlevel [!2345]

setuid root

respawn

script
  java -jar /home/kohsuke/elb-dns-1.0-SNAPSHOT-jar-with-dependencies.jar -b elb.kohsuke.org
end script
```

In the parent DNS, you need to designate this custom DNS server as the delegation target:

```
elb.kohsuke.org   NS    host.where.I.run.dns.
```

## Test Drive
Launch an ELB, and wait for that to become available:

```
$ nslookup bar-1984295099.us-west-2.elb.amazonaws.com
Server:		192.168.250.1
Address:	192.168.250.1#53

Non-authoritative answer:
Name:	bar-1984295099.us-west-2.elb.amazonaws.com
Address: 54.214.28.101
```

Now you can do this:

```
$ nslookup any.prefix.bar-1984295099.us-west-2.elb.kohsuke.org
Server:		192.168.250.1
Address:	192.168.250.1#53

Non-authoritative answer:
any.prefix.bar-1984295099.us-west-2.elb.kohsuke.org	canonical name = bar-1984295099.us-west-2.elb.amazonaws.com.
Name:	bar-1984295099.us-west-2.elb.amazonaws.com
Address: 54.214.28.101
```

You are welcome to use `*.elb.kohsuke.org` or run your own instance.