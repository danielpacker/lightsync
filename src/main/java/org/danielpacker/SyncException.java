package org.danielpacker;

class SyncException extends Exception
{
    public SyncException(String s)
    {
        super(s);
    }
}

class SyncOverflowException extends Exception
{
    public SyncOverflowException(String s)
    {
        super(s);
    }
}