// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract Blacklistable {
    address public owner;
    mapping(address => bool) private _blacklist;

    event Blacklisted(address indexed account);
    event Unblacklisted(address indexed account);

    modifier onlyOwner() {
        require(msg.sender == owner, "Only owner can call this function");
        _;
    }

    modifier notBlacklisted(address account) {
        require(!_blacklist[account], "Address is blacklisted");
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    function isBlacklisted(address account) public view returns (bool) {
        return _blacklist[account];
    }

    function blacklist(address account) public onlyOwner {
        _blacklist[account] = true;
        emit Blacklisted(account);
    }

    function unblacklist(address account) public onlyOwner {
        _blacklist[account] = false;
        emit Unblacklisted(account);
    }
}
